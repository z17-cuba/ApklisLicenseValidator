# ApklisLicenseValidator

La biblioteca en Kotlin para la validación de licencias a través de la plataforma Apklis. Esta biblioteca proporciona métodos para comprar y verificar licencias en aplicaciones Android.

## Instalación

1. Añade esto al archivo `build.gradle` de tu aplicación:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```
2. A continuación, dirígete a [la página web de Jitpack](https://jitpack.io/#z17-cuba/ApklisLicenseValidator) para comprobar cuál es la última versión de la librería e importarla a tu aplicación.
La última versión hasta la fecha es:

```gradle
dependencies {
    //Recuerda siempre consultar la última versión de la biblioteca.
   implementation("com.github.z17-cuba:ApklisLicenseValidator:v.1.0.3")
}
```

3. Por último, la clave de cifrado generada para cada desarrollador (única para cada grupo de licencias) debe colocarse en la ruta **android/app/src/main/assets/license_private_key.pub**. Esta clave se utiliza para realizar la comprobación de cifrado con el fin de validar que la solicitud proviene de una fuente fiable y emitir la validación correspondiente.

## Uso

La biblioteca proporciona dos métodos principales:
- `purchaseLicense()`: compra una licencia con un UUID determinado.
- `verifyCurrentLicense()`: verifica la licencia actual de un paquete.
La biblioteca también proporciona un manejo estructurado de errores a través de la clase `LicenseError`.

### 1. Uso basado en instancias
Cree una instancia de `ApklisLicenseValidator` y llame a los métodos que contiene:

#### Comprar licencia
```kotlin
val validator = ApklisLicenseValidator()

// Sin webhook
validator.purchaseLicense(context, "your-license-uuid", object : LicenseCallback {
override fun onSuccess(response: Map<String, Any>) {
// License purchased successfully
Log.d("License", "Purchase successful: $response")

        // Handle the response data
        val success = response["success"] as? Boolean ?: false
        val paid = response["paid"] as? Boolean ?: false
        val license = response["license"] as? String ?: ""
        val username = response["username"] as? String ?: ""
        
        if (success && paid) {
            // Payment completed successfully
            Log.d("License", "Payment completed for license: $license")
            enablePremiumFeatures()
        } else if (response.containsKey("error")) {
            // Handle error cases
            val error = response["error"] as? String ?: "Unknown error"
            Log.e("License", "Purchase error: $error")
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle purchase error
        Log.e("License", "Purchase failed: ${error.message}")
        
        when (error.code) {
            "INVALID_ARGUMENT" -> {
                // Handle invalid license UUID
            }
            "PURCHASE_ERROR" -> {
                // Handle purchase failure
            }
            "PURCHASE_FAILED" -> {
                // Handle no response from server
            }
        }
    }
})

// Con webhook opcional
validator.purchaseLicense(
    context,
    "your-license-uuid",
    "https://tu-servidor.com/webhook",
    object : LicenseCallback {
        override fun onSuccess(response: Map<String, Any>) {
            // ...
        }

        override fun onError(error: LicenseError) {
            // ...
        }
    }
)
```

#### Verificar licencia activa
```kotlin
val validator = ApklisLicenseValidator()

validator.verifyCurrentLicense(context, "com.example.myapp", object : LicenseCallback {
    override fun onSuccess(response: Map<String, Any>) {
        // License verification successful
        Log.d("License", "Verification successful: $response")
        
        // Handle the response data
        val license = response["license"] as? String ?: ""
        val paid = response["paid"] as? Boolean ?: false
        val username = response["username"] as? String ?: ""
        
        if (paid && license.isNotEmpty()) {
            // Valid license found
            Log.d("License", "Valid license found: $license for user: $username")
            enablePremiumFeatures()
        } else {
            // No valid license
            Log.d("License", "No valid license found")
            disablePremiumFeatures()
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle verification error
        Log.e("License", "Verification failed: ${error.message}")
        
        when (error.code) {
            "INVALID_ARGUMENT" -> {
                // Handle invalid package ID
            }
            "VERIFY_ERROR" -> {
                // Handle verification failure
            }
            "VERIFICATION_FAILED" -> {
                // Handle no response from server
            }
        }
    }
})
```

### 2. Métodos de acceso estáticos
Utilice los métodos de acceso estáticos de `ApklisLicenseUtils` para un acceso rápido:

#### Comprar licencia
```kotlin
ApklisLicenseUtils.purchaseLicense(context, "your-license-uuid", object : LicenseCallback {
    override fun onSuccess(response: Map<String, Any>) {
        // License purchased successfully
        Log.d("License", "Purchase successful: $response")
        
        // Process the response
        val success = response["success"] as? Boolean ?: false
        val paid = response["paid"] as? Boolean ?: false
        val license = response["license"] as? String ?: ""
        val username = response["username"] as? String ?: ""
        
        if (success && paid) {
            // Payment completed successfully
            Log.d("License", "Payment completed for license: $license")
            enablePremiumFeatures()
        } else if (response.containsKey("error")) {
            // Handle error within success response
            val error = response["error"] as? String ?: "Unknown error"
            showErrorDialog("Purchase Error", error)
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle purchase error
        Log.e("License", "Purchase failed: ${error.message}")
        
        // Show error message to user
        showErrorDialog("Purchase Failed", error.message)
    }
})
```

#### Verificar licencia activa
```kotlin
ApklisLicenseUtils.verifyCurrentLicense(context, "com.example.myapp", object : LicenseCallback {
    override fun onSuccess(response: Map<String, Any>) {
        // License verification successful
        Log.d("License", "Verification successful: $response")
        
        // Process the response
        val license = response["license"] as? String ?: ""
        val paid = response["paid"] as? Boolean ?: false
        val username = response["username"] as? String ?: ""
        
        if (paid && license.isNotEmpty()) {
            // License is valid, enable features
            Log.d("License", "Valid license found: $license for user: $username")
            enablePremiumFeatures()
        } else {
            // No valid license found
            Log.d("License", "No valid license found")
            disablePremiumFeatures()
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle verification error
        Log.e("License", "Verification failed: ${error.message}")
        
        // Disable premium features on verification failure
        disablePremiumFeatures()
    }
})
```

## Formato de respuesta

### Respuesta de compra de licencia

Si la compra se ha realizado correctamente, la respuesta contiene:
```kotlin
// Pago completado con éxito
mapOf(
    "success" to true,
    "paid" to true,
    "license" to "license-name-string",
    "username" to "user-account-name"
)

// El usuario ha cerrado el cuadro de diálogo de pago
mapOf(
    "success" to false,
    "paid" to false,
    "error" to "Dialog closed by user",
    "username" to "user-account-name"
)

// Error durante el proceso de pago
mapOf(
    "error" to "error-message",
    "paid" to false,
    "username" to "user-account-name"
)
```

###  Respuesta de verificación de licencia activa
Si la verificación se realiza correctamente, la respuesta contiene:

```kotlin
// Se ha encontrado una licencia válida.
mapOf(
    "license" to "license-name-string",
    "paid" to true,
    "username" to "user-account-name"
)

// No hay licencia válida
mapOf(
    "license" to "",
    "paid" to false,
    "username" to "user-account-name"
)

//  Error durante la verificación
mapOf(
    "error" to "error-message",
    "username" to "user-account-name",
    "status_code" to httpStatusCode  // Opcional, solo en errores de API
)
```

## Manejo de errores
```kotlin
data class LicenseError(
    val code: String,        // Código de error (por ejemplo, «PURCHASE_ERROR», «VERIFY_ERROR»)
    val message: String,     // Mensaje de error legible para el usuario
    val exception: Exception? // Excepción original, si está disponible
)
```

###  Códigos de error comunes

| Código | Descripción |
|------|-------------|
| `INVALID_ARGUMENT` | UUID de licencia/ID de paquete no válido o vacío |
| `PURCHASE_ERROR` | Error general en la operación de compra |
| `PURCHASE_FAILED` | No se ha recibido respuesta de la operación de compra |
| `VERIFY_ERROR` | Error general en la operación de verificación |
| `VERIFICATION_FAILED` | No se ha recibido respuesta de la operación de verificación |


## Estructura y clases nativas de Kotlin

- **📁 api_helpers**: Carpeta con las clases requeridas para hacer las peticiones a la API de Apklis (**`ApiService.kt`** 🌐), para un wrapper de las respuestas de la API, ya sean de éxito o error y manejar de forma más eficiente cada estado de la verificación (**`ApiResult.kt`** ✅❌), y el interceptor para leer y probar de forma más cómoda el intercambio entre la API y el plugin (**`LoggingInterceptor.kt`** 📋).

- **📁 models**: Carpeta con las clases de dato (o modelos) requeridas para hacer/o leer las peticiones a la API de Apklis 📄.

- **📁 signature_helper**: Carpeta con la clase que se encarga de validar que la petición a la API de Apklis y su respuesta se realizan de forma segura y sin intermediarios (**`SignatureVerificationService.kt`** 🔐).

- **🔌 `ApklisDataGetter.kt`**: Clase se llama mediante la app de ejemplo en Flutter para obtener los datos del Provider expuesto en la app de Apklis necesarios para la validación.

- **🔌 `ApklisLicenseValidatorPlugin.kt`**: Clase padre que se llama mediante la app de ejemplo en Flutter que reconoce los métodos llamativos y devuelve los valores/errores.

- **⚙️ `PurchaseAndVerify.kt`**: Clase que contiene los métodos a llamar desde la clase padre **`ApklisLicenseValidatorPlugin`** y que contiene la lógica de la verificación y pago de licencias 💳.

- **📱 `QRDialogManager.kt`**: Clase que se encarga de manejar, dibujar y mostrar el código QR del pago de Transfermóvil 📲.

- **🔌 `WebSocketClient.kt` + `WebSocketService.kt`**: Clase (y servicio) que se encarga de conectarse a un servidor WebSocket para la retroalimentación inmediata del pago y el estado de la licencia en el dispositivo (incluso en 2do plano) ⚡. De forma automática se encarga de la conexión al canal de la licencia asociada al dispositivo y al usuario, de la reconexión cada cierto tiempo para evitar desconexiones y de cerrar la conexión cuando ha terminado para ahorrar recursos 🔄.


### FAQs - Errores conocidos

Este error fue reportado en un Xiaomi Redmi Note 11 con Android 11 (pero no está ligado solo a ese dispositivo específico):
Si te da error 403 con las credenciales de manera repetida, y ya agotaste las opciones:
1. Iniciar sesión
2. Hacer alguna acción para se refresque el token si expiró
3. Cerrar sesión y volver a iniciar
4. Revisar que Apklis esté en 2do plano
5. Revisar en la sección de Ajustes del teléfono -> Cuentas y sincronización, y comprobar que el usuario/cuenta de Apklis se está creando correctamente

Entonces se sugiere agregar esta línea en el Android Manifest de su aplicación:
```xml
<queries>
<package android:name="cu.uci.android.apklis" />
</queries>
```

## Android 16
Aplica para el mismo error de arriba pero para dispositivos con Android 16.
Fue probado y funciona con un Google Pixel 8 y en un Samsung S24
```xml

    <queries>
        <intent> 
            <action android:name="android.accounts.AccountAuthenticator" /> 
        </intent> 
        <intent> 
            <action android:name="android.intent.action.VIEW" /> 
            <data android:scheme="apklis" /> 
        </intent> 
    </queries>
```


## Ejemplo completo
A continuación se muestra un ejemplo completo que muestra ambos métodos en una actividad de Android:

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Example 1: Instance-based usage
        val validator = ApklisLicenseValidator()

        findViewById<Button>(R.id.btnPurchase).setOnClickListener {
            purchaseLicenseExample(validator)
        }

        findViewById<Button>(R.id.btnVerify).setOnClickListener {
            verifyLicenseExample()
        }
    }

    private fun purchaseLicenseExample(validator: ApklisLicenseValidator) {
        validator.purchaseLicense(this, "sample-license-uuid", object : LicenseCallback {
            override fun onSuccess(response: Map<String, Any>) {
                runOnUiThread {
                    val success = response["success"] as? Boolean ?: false
                    val paid = response["paid"] as? Boolean ?: false
                    val license = response["license"] as? String ?: ""

                    if (success && paid) {
                        Toast.makeText(this@MainActivity, "License purchased: $license", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = response["error"] as? String ?: "Unknown error"
                        Toast.makeText(this@MainActivity, "Purchase failed: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(error: LicenseError) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Purchase failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun verifyLicenseExample() {
        // Example 2: Static utility method
        ApklisLicenseUtils.verifyCurrentLicense(this, packageName, object : LicenseCallback {
            override fun onSuccess(response: Map<String, Any>) {
                runOnUiThread {
                    val license = response["license"] as? String ?: ""
                    val paid = response["paid"] as? Boolean ?: false

                    if (paid && license.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "Valid license: $license", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "No valid license found", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(error: LicenseError) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Verification failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
```
