package cu.uci.android.apklis_license_validator.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class QrCode(
    @SerializedName("id_transaccion") val idTransaccion: String,
    @SerializedName("importe") val importe: String,
    @SerializedName("moneda") val moneda: String,
    @SerializedName("numero_proveedor") val numeroProveedor: String,
    @SerializedName("version") val version: String,
)  : Parcelable {
    fun toJsonString(): String {
        return """{"id_transaccion": "$idTransaccion", "importe": "$importe", "moneda": "$moneda", "numero_proveedor": "$numeroProveedor", "version": "$version"}"""
    }

}