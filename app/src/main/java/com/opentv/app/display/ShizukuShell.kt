package com.opentv.app.display

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * Puente con Shizuku: una app aparte que, una vez activada por el usuario (depuración inalámbrica), presta permisos
 * de «shell». OpenTV la usa solo para `wm size` y `wm density`. Sin Shizuku activo, nada de esto funciona y OpenTV
 * lo dice; no hay otra forma de cambiar la resolución sin root.
 */
class ShizukuShell(private val context: Context) : ShellRunner {

    enum class Status(val message: String) {
        NOT_INSTALLED("Shizuku no está instalada. Instalala y activala para forzar 16:9."),
        NOT_RUNNING("Shizuku está instalada pero no está activa. Abrila y activala (depuración inalámbrica)."),
        UNSUPPORTED("La versión de Shizuku instalada es muy vieja. Actualizala."),
        NEEDS_PERMISSION("Shizuku está activa. Falta darle permiso a OpenTV."),
        READY("Shizuku lista."),
    }

    @Volatile private var service: IUserService? = null

    fun status(): Status = try {
        when {
            Shizuku.pingBinder() -> when {
                Shizuku.isPreV11() -> Status.UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
                else -> Status.NEEDS_PERMISSION
            }
            isInstalled() -> Status.NOT_RUNNING
            else -> Status.NOT_INSTALLED
        }
    } catch (e: Throwable) {
        Status.NOT_RUNNING
    }

    private fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Muestra el pedido de permiso de Shizuku; [onResult] recibe si lo concedió. */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        try {
            if (!Shizuku.pingBinder()) {
                onResult(false)
                return
            }
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            onResult(false)
        }
    }

    /**
     * Ejecuta un comando `wm` con permisos de shell. Primero por el camino directo (Shizuku lanza el proceso él mismo,
     * sin servicio auxiliar); si falla, por el servicio auxiliar. Si ambos fallan, el error cuenta cada motivo.
     */
    override suspend fun exec(command: String): String {
        require(ScreenResizer.isAllowedCommand(command)) { "comando no permitido: $command" }
        val errors = mutableListOf<String>()
        try {
            return viaProcess(command)
        } catch (e: TimeoutCancellationException) {
            errors += "directo: no respondió en ${EXEC_TIMEOUT_MS / 1000} s"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            errors += "directo: ${describe(e)}"
        }
        try {
            return viaService(command)
        } catch (e: TimeoutCancellationException) {
            errors += "servicio auxiliar: no arrancó en ${CONNECT_TIMEOUT_MS / 1000} s"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            errors += "servicio auxiliar: ${describe(e)}"
        }
        throw IllegalStateException(errors.joinToString(" | "))
    }

    private fun describe(e: Throwable): String {
        val cause = (e as? InvocationTargetException)?.targetException ?: e
        return "${cause.javaClass.simpleName}: ${cause.message.orEmpty().take(120)}"
    }

    /**
     * `Shizuku.newProcess` es privado desde la API 13 (se recomienda el servicio auxiliar), pero sigue existiendo y es
     * lo más directo: un `sh -c` con permisos de shell sin arrancar otro proceso con nuestras clases.
     */
    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
    }

    private suspend fun viaProcess(command: String): String = withTimeout(EXEC_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", "$command 2>&1"), null, null) as Process
            cont.invokeOnCancellation { runCatching { process.destroy() } }
            Thread({
                try {
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    if (cont.isActive) cont.resume(output)
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }, "opentv-shizuku-exec").apply { isDaemon = true }.start()
        }
    }

    private suspend fun viaService(command: String): String = withContext(Dispatchers.IO) {
        val s = service ?: connect()
        s.exec(command)
    }

    private suspend fun connect(): IUserService = withTimeout(CONNECT_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
                .daemon(false)
                .processNameSuffix("shell")
                .version(SERVICE_VERSION)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder != null && binder.pingBinder()) {
                        val s = IUserService.Stub.asInterface(binder)
                        service = s
                        if (cont.isActive) cont.resume(s)
                    } else if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Shizuku no entregó el servicio."))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            Shizuku.bindUserService(args, connection)
            cont.invokeOnCancellation { runCatching { Shizuku.unbindUserService(args, connection, true) } }
        }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4102
        const val SERVICE_VERSION = 1
        const val CONNECT_TIMEOUT_MS = 4_000L
        const val EXEC_TIMEOUT_MS = 6_000L
    }
}
