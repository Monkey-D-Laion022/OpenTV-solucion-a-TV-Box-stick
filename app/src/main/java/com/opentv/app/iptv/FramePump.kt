package com.opentv.app.iptv

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Bomba de cuadros a ritmo fijo entre la captura de pantalla y el codificador.
 *
 * Problema que resuelve: la pantalla virtual de MediaProjection solo entrega un cuadro cuando CAMBIA algo en pantalla.
 * Con el codificador conectado directo, al quedar la pantalla quieta (por ejemplo al salir de OpenTV a un fondo estático)
 * dejan de llegar cuadros: el codificador no produce fotogramas clave, los segmentos HLS no se cierran, el TS se
 * detiene y el televisor corta. Además el ritmo de cuadros pasa a depender del contenido (los «FPS» bajan solos).
 *
 * Cómo: la pantalla virtual dibuja en una [SurfaceTexture] propia; un hilo con contexto OpenGL ES dibuja la última
 * imagen recibida en la superficie del codificador [fps] veces por segundo, haya o no cuadros nuevos. Así el codificador
 * recibe un ritmo constante (CFR) y los fotogramas clave salen cada segundo. Si algo falla al iniciar, [start] lanza
 * una excepción y quien llama usa la captura directa de antes. NO verificado en dispositivos: la comprobación real son
 * los contadores [capturedFrames] y [renderedFrames] que muestra la pantalla.
 */
class FramePump(
    private val encoderSurface: Surface,
    private val width: Int,
    private val height: Int,
    fps: Int,
) {
    private val thread = HandlerThread("opentv-pump", Process.THREAD_PRIORITY_DISPLAY)
    private var handler: Handler? = null
    private var started = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var texture = 0
    private var program = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var input: Surface? = null
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private val texMatrix = FloatArray(16)
    private val vertices: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texCoords: FloatBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    private val fresh = AtomicBoolean(false)
    @Volatile private var hasFrame = false
    @Volatile private var running = false
    private var nextNs = 0L
    /** Intervalo entre cuadros; el gobernador de FPS lo puede cambiar en vivo (no exige otro permiso ni reiniciar la captura). */
    @Volatile private var intervalNs = 1_000_000_000L / fps.coerceIn(1, 120)

    /** Cambia el ritmo de redibujado. Solo baja o sube la cadencia de repetición; la resolución no se toca. */
    fun setFps(newFps: Int) { intervalNs = 1_000_000_000L / newFps.coerceIn(1, 120) }

    /** Cuadros nuevos que llegaron desde la captura de pantalla (lo que realmente cambió en pantalla). */
    val capturedFrames = AtomicLong(0)

    /** Cuadros entregados al codificador (incluye repeticiones de la última imagen). */
    val renderedFrames = AtomicLong(0)

    /** Texto del primer error en tiempo de ejecución, si el dibujo se detuvo. */
    @Volatile var failure: String? = null
        private set

    /** Superficie donde debe dibujar la pantalla virtual. */
    @Volatile var captureSurface: Surface? = null
        private set

    /** Lanza una excepción (ya liberando lo abierto) si el teléfono no puede crear el contexto de dibujo. */
    fun start(): Surface {
        thread.start()
        started = true
        val h = Handler(thread.looper)
        handler = h
        val latch = CountDownLatch(1)
        val error = AtomicReference<Throwable?>(null)
        h.post {
            try {
                initGl()
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(4, TimeUnit.SECONDS)
        val problem: Throwable? = error.get()
        val surface: Surface? = captureSurface
        if (!ok || problem != null || surface == null) {
            stop()
            throw IllegalStateException(
                if (!ok) "El contexto de dibujo no inició a tiempo" else "No se pudo crear el contexto de dibujo (${problem?.javaClass?.simpleName}: ${problem?.message})",
            )
        }
        running = true
        nextNs = System.nanoTime()
        h.post(tick)
        return surface
    }

    fun stop() {
        running = false
        val h = handler
        if (started && h != null) {
            h.removeCallbacks(tick)
            val latch = CountDownLatch(1)
            val posted = h.post {
                try { releaseGl() } finally { latch.countDown() }
            }
            if (posted) latch.await(2, TimeUnit.SECONDS)
        }
        if (started) thread.quitSafely()
        started = false
        handler = null
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                if (fresh.getAndSet(false)) {
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(texMatrix)
                    hasFrame = true
                }
                if (hasFrame) {
                    draw()
                    renderedFrames.incrementAndGet()
                }
            } catch (e: RuntimeException) {
                failure = "${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}"
                running = false
                return
            }
            nextNs += intervalNs
            val now = System.nanoTime()
            if (nextNs < now - 4 * intervalNs) nextNs = now // se atrasó mucho: no acumular deuda
            val delayMs = ((nextNs - now) / 1_000_000L).coerceAtLeast(0L)
            handler?.postDelayed(this, delayMs)
        }
    }

    private fun draw() {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 8, vertices)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 8, texCoords)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        // Hora del cuadro en el reloj monotónico del sistema, el mismo que usa el resto de la tubería.
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, System.nanoTime())
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) throw IllegalStateException("eglSwapBuffers falló (0x${Integer.toHexString(EGL14.eglGetError())})")
    }

    private fun initGl() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay devolvió EGL_NO_DISPLAY" }
        eglDisplay = display
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize falló" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "no hay configuración EGL grabable" }
        val config: EGLConfig = configs[0] ?: throw IllegalStateException("configuración EGL nula")
        val ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(ctx != EGL14.EGL_NO_CONTEXT) { "eglCreateContext falló" }
        eglContext = ctx
        val surf = EGL14.eglCreateWindowSurface(display, config, encoderSurface, intArrayOf(EGL14.EGL_NONE), 0)
        check(surf != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface falló" }
        eglSurface = surf
        check(EGL14.eglMakeCurrent(display, surf, surf, ctx)) { "eglMakeCurrent falló" }

        program = buildProgram(VERTEX, FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.Matrix.setIdentityM(texMatrix, 0)

        val st = SurfaceTexture(texture)
        st.setDefaultBufferSize(width, height)
        st.setOnFrameAvailableListener(
            SurfaceTexture.OnFrameAvailableListener {
                capturedFrames.incrementAndGet()
                fresh.set(true)
            },
            handler,
        )
        surfaceTexture = st
        captureSurface = Surface(st).also { input = it }
    }

    private fun releaseGl() {
        runCatching { input?.release() }
        runCatching { surfaceTexture?.release() }
        input = null
        surfaceTexture = null
        captureSurface = null
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            runCatching { EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
            if (eglSurface != EGL14.EGL_NO_SURFACE) runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
            if (eglContext != EGL14.EGL_NO_CONTEXT) runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
            runCatching { EGL14.eglTerminate(eglDisplay) }
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun buildProgram(vertex: String, fragment: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        check(p != 0) { "glCreateProgram falló" }
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "no se pudo enlazar el programa gráfico: ${GLES20.glGetProgramInfoLog(p)}" }
        return p
    }

    private fun compile(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        check(s != 0) { "glCreateShader falló" }
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        val status = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "no se pudo compilar el shader: ${GLES20.glGetShaderInfoLog(s)}" }
        return s
    }

    companion object {
        /** `EGL_RECORDABLE_ANDROID` (extensión EGL_ANDROID_recordable): la superficie del codificador la exige. */
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private fun floatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    }
}
