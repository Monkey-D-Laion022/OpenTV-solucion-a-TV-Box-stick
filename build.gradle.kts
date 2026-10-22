// Archivo raíz: solo declara los plugins; se aplican en cada módulo.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
