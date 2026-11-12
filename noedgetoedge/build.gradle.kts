plugins {
    id("com.android.library")
}

android {
    namespace = "com.macruware.noedgetoedge"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Debe ser menor o igual al minSdk de la app que lo use.
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Solo para cubrir los DialogFragment de pantalla completa. Es la misma versión que ya
    // trae appcompat 1.6.1, así que no sube la versión de fragment de ninguna app.
    implementation("androidx.fragment:fragment:1.3.6")
}
