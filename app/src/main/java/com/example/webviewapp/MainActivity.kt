package io.github.aulalghifary_arch.twa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream

// ===== TAMBAHAN: Play Billing =====
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

// TAMBAHAN: Product ID langganan, sudah cocok dengan yang terdaftar di Play Console
private const val PREMIUM_PRODUCT_ID = "premium_bulanan"

// TAMBAHAN: ", PurchasesUpdatedListener" ditambahkan di deklarasi class ini
class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    // ===== TAMBAHAN: Play Billing =====
    private lateinit var billingClient: BillingClient
    private var cachedProductDetails: ProductDetails? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            uploadMessage?.onReceiveValue(results)
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = cariSwipeRefresh(findViewById(android.R.id.content))

        // FIX GARIS PUTIH/ABU-ABU DI BAWAH STATUS BAR (khusus APK):
        // Garis itu adalah <ProgressBar> horizontal di paling atas layout (4dp, selebar
        // layar, persis di bawah status bar) yang track kosongnya tidak ikut menyesuaikan
        // mode gelap/terang. PENTING: mode gelap/terang aplikasi ini diatur SENDIRI oleh
        // web (tombol "Ganti Mode Gelap/Terang" di bebasgaris.html, disimpan di
        // localStorage), BUKAN mengikuti mode gelap/terang sistem HP. Makanya warna
        // baris ini tidak boleh ditebak dari resources.configuration, melainkan harus
        // menunggu laporan dari web lewat jembatan Android.setTemaStatusBar() yang
        // memang sudah disiapkan di bebasgaris.html (dipanggil saat window.onload dan
        // setiap tombol mode ditekan). Di bawah ini cuma tebakan awal sekilas sebelum
        // laporan asli dari web itu datang; lihat fungsi terapkanTemaNative().
        val modeGelapAwal = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        terapkanTemaNative(modeGelapAwal)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        swipeRefreshLayout?.setOnRefreshListener {
            webView.reload()
        }

        // BUG FIX: Deteksi scroll native Android agar tidak bentrok dengan SwipeRefresh
        swipeRefreshLayout?.isEnabled = false


        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout?.isRefreshing = false
                view?.evaluateJavascript("window.print = function() { Android.printInvoice(); };", null)

                // BUG FIX: Suntikkan sensor pintar ke dalam JavaScript website
                // Ini untuk mendeteksi scroll pada website modern yang menggunakan layout kontainer internal (100vh)
                val jsScrollSensor = """
                    (function() {
                        var checkScroll = function() {
                            var isAtTop = window.scrollY === 0 && document.documentElement.scrollTop === 0;
                            Android.setSwipeRefreshEnable(isAtTop);
                        };
                        window.addEventListener('scroll', checkScroll);
                        
                        // Cari seluruh elemen box yang bisa di-scroll di dalam web
                        document.querySelectorAll('*').forEach(function(el) {
                            var styles = window.getComputedStyle(el);
                            if (styles.overflowY === 'auto' || styles.overflowY === 'scroll') {
                                el.addEventListener('scroll', function() {
                                    Android.setSwipeRefreshEnable(el.scrollTop === 0);
                                });
                            }
                        });
                    })();
                """.trimIndent()
                view?.evaluateJavascript(jsScrollSensor, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback

                val intent = fileChooserParams?.createIntent()
                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    uploadMessage = null
                    Toast.makeText(this@MainActivity, "Gagal membuka file manager", Toast.LENGTH_SHORT).show()
                    return false
                }
                return true
            }
        }

        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        // DIUBAH: URL sekarang mengarah ke PWA Buku Kas Aul (sebelumnya proyek-kas-prod.vercel.app)
        webView.loadUrl("https://aulalghifary-arch.github.io/Buku-Kas-Aul-Gen2.5/")



        webView.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                val jsDataConverter = """
                    (function() {
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', '$url', true);
                        xhr.responseType = 'blob';
                        xhr.onload = function() {
                            if (xhr.status === 200) {
                                var reader = new FileReader();
                                reader.readAsDataURL(xhr.response);
                                reader.onloadend = function() {
                                    var base64Data = reader.result;
                                    Android.prosesDownload(base64Data);
                                }
                            }
                        };
                        xhr.send();
                    })();
                """.trimIndent()
                webView.evaluateJavascript(jsDataConverter, null)
                Toast.makeText(this, "Memproses file dokumen...", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuka tautan download", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // TAMBAHAN: siapkan koneksi Play Billing
        setupBillingClient()
    }

    private fun cariSwipeRefresh(view: View): SwipeRefreshLayout? {
        if (view is SwipeRefreshLayout) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = cariSwipeRefresh(view.getChildAt(i))
                if (child != null) return child
            }
        }
        return null
    }

    // Fungsi pembantu untuk mengontrol aktif/tidaknya swipe-refresh dari jembatan JavaScript
    fun aturStatusRefresh(diAtasMaksimal: Boolean) {
        runOnUiThread {
            swipeRefreshLayout?.isEnabled = diAtasMaksimal
        }
    }

    // Dipanggil dari WebAppInterface.setTemaStatusBar() setiap kali web (bebasgaris.html)
    // melaporkan mode gelap/terangnya sendiri (saat halaman dibuka & saat tombol mode
    // ditekan). Menyamakan warna track ProgressBar (garis di bawah status bar), warna
    // latar WebView, dan status bar native, dengan mode yang BENAR-BENAR aktif di web,
    // bukan mode gelap/terang sistem HP.
    fun terapkanTemaNative(modeGelap: Boolean) {
        runOnUiThread {
            val warnaLatar = if (modeGelap) {
                Color.parseColor("#121212")
            } else {
                Color.parseColor("#f4f7f6")
            }
            webView.setBackgroundColor(warnaLatar)
            progressBar.progressBackgroundTintList = ColorStateList.valueOf(warnaLatar)

            window.statusBarColor = warnaLatar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (modeGelap) {
                    window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else {
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }
    }

    fun buatCetak() {
        runOnUiThread {
            try {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Dokumen Buku Kas")
                printManager.print("Dokumen Buku Kas", printAdapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal memuat sistem printer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun simpanFile(base64Data: String) {
        try {
            val mimeType = if (base64Data.contains(";")) {
                base64Data.substringAfter("data:").substringBefore(";")
            } else {
                "application/octet-stream"
            }

            val pureBase64 = base64Data.substringAfter("base64,")
            val fileBytes = Base64.decode(pureBase64, Base64.DEFAULT)

            val extension = when {
                mimeType.contains("pdf") -> ".pdf"
                mimeType.contains("json") -> ".json"
                mimeType.contains("png") -> ".png"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                else -> ".bin"
            }

            val fileName = "BukuKas_${System.currentTimeMillis()}$extension"
            val mimeTypeToSave = if (extension == ".pdf") "application/pdf" else if (extension == ".json") "application/json" else "application/octet-stream"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeTypeToSave)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(fileBytes)
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(fileBytes)
                }
            }

            runOnUiThread {
                Toast.makeText(this, "Berhasil! Cek folder Download di HP Anda.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Gagal menyimpan file ke memori", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========================================================================
    // TAMBAHAN: BAGIAN PLAY BILLING (semuanya baru, tidak mengubah kode di atas)
    // ========================================================================

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Akan dicoba sambung ulang otomatis saat beliPremium() dipanggil lagi
            }
        })
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = productDetailsResult.productDetailsList
                if (!details.isNullOrEmpty()) {
                    cachedProductDetails = details[0]
                }
            }
        }
    }

    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val aktif = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (aktif) {
                    notifyWeb("onPremiumPurchased", "")
                }
            }
        }
    }

    private fun launchPurchaseFlow() {
        val productDetails = cachedProductDetails ?: run {
            notifyWeb("onPremiumError", "Produk belum siap, coba lagi sebentar lagi.")
            return
        }
        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()?.offerToken ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(this, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                notifyWeb("onPremiumCancelled", "")
            }
            else -> {
                notifyWeb("onPremiumError", billingResult.debugMessage)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(ackParams) {
                    notifyWeb("onPremiumPurchased", "")
                }
            } else {
                notifyWeb("onPremiumPurchased", "")
            }
        }
    }

    /** Dipanggil dari WebAppInterface.beliPremium() lewat jembatan "Android" yang sudah ada */
    fun beliPremium() {
        runOnUiThread { launchPurchaseFlow() }
    }

    private fun notifyWeb(jsFunctionName: String, message: String) {
        runOnUiThread {
            val safeMessage = message.replace("'", "\\'")
            webView.evaluateJavascript(
                "if (window.$jsFunctionName) { window.$jsFunctionName('$safeMessage'); }",
                null
            )
        }
    }
}

class WebAppInterface(private val mContext: MainActivity) {
    @JavascriptInterface
    fun printInvoice() {
        mContext.buatCetak()
    }

    @JavascriptInterface
    fun prosesDownload(base64Data: String) {
        mContext.simpanFile(base64Data)
    }

    // Jembatan khusus untuk menerima data posisi scroll dari website
    @JavascriptInterface
    fun setSwipeRefreshEnable(isAtTop: Boolean) {
        mContext.aturStatusRefresh(isAtTop)
    }

    // Jembatan yang sudah dipanggil dari bebasgaris.html (beritahuNativeTemaStatusBar)
    // tapi sebelumnya belum ada method-nya di sisi Android, jadi selalu diabaikan diam-diam.
    @JavascriptInterface
    fun setTemaStatusBar(modeGelap: Boolean) {
        mContext.terapkanTemaNative(modeGelap)
    }

    // TAMBAHAN: dipanggil dari script.js lewat Android.beliPremium()
    @JavascriptInterface
    fun beliPremium() {
        mContext.beliPremium()
    }
}
