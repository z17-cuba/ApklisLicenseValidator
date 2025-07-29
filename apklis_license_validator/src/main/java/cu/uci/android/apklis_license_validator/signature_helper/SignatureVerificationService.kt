package cu.uci.android.apklis_license_validator.signature_helpers

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class SignatureVerificationService {
    private val keyFilename = "license_private_key.pub"

    /**
     * Create a File from ByteArray by writing to storage
     */
    private fun createFileFromBytes(context: Context, bytes: ByteArray): File? {
        return try {
            val file = File(context.filesDir, keyFilename)
            FileOutputStream(file).use { output ->
                output.write(bytes)
            }
            file
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun loadFileFromAssetsAsBytes(context: Context,): ByteArray? {
        return try {
            val inputStream: InputStream = context.assets.open(keyFilename)
            val bytes = inputStream.readBytes()
            inputStream.close()
            bytes
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun verifySignature(context: Context, data: ByteArray, signatureBase64: String): Boolean {
        fun parsePemToDer(pem: String): ByteArray {
            val cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            return Base64.getDecoder().decode(cleaned)
        }

        return try {

            val bytes = loadFileFromAssetsAsBytes( context)

             if (bytes != null) {
                 val publicKeyFile:  File? = createFileFromBytes(context,  bytes)

                 if(publicKeyFile != null) {
                     val pemContent = publicKeyFile.readText(Charsets.UTF_8)
                     val publicKeyBytes = parsePemToDer(pemContent)

                     // Convertir a PublicKey
                     val keySpec = X509EncodedKeySpec(publicKeyBytes)
                     val keyFactory = KeyFactory.getInstance("RSA")
                     val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

                     // Decodificar la firma en base64
                     val signatureBytes = Base64.getDecoder().decode(signatureBase64)

                     // Verificar la firma
                     val signature = Signature.getInstance("SHA256withRSA")
                     signature.initVerify(publicKey)
                     signature.update(data)

                     signature.verify(signatureBytes)
                 } else{
                     false
                 }
            } else{
                false
            }

        } catch (e: Exception) {
            false
        }
    }


}