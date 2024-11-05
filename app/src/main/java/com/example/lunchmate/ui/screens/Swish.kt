    package com.example.lunchmate.ui.screens

    import android.content.Context
    import okhttp3.OkHttpClient
    import okhttp3.Request
    import com.example.lunchmate.R
    import java.io.InputStream
    import java.security.KeyStore
    import javax.net.ssl.SSLContext
    import javax.net.ssl.TrustManagerFactory
    import javax.net.ssl.KeyManagerFactory
    import javax.net.ssl.X509TrustManager
    import okhttp3.MediaType.Companion.toMediaType
    import okhttp3.RequestBody.Companion.toRequestBody
    import okhttp3.Response
    import java.security.cert.CertificateFactory
    import android.util.Log
    import com.google.firebase.firestore.FirebaseFirestore
    import okhttp3.*
    import java.io.IOException
    import javax.net.ssl.*

// Function to create OkHttp client with Swish client certificate
fun createSwishOkHttpClient(context: Context): OkHttpClient {
    try {
        // Load the client certificate from res/raw
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            val certInputStream: InputStream = context.resources.openRawResource(R.raw.certificate) // Your client certificate
            load(certInputStream, "123456".toCharArray()) // Your client certificate password
            certInputStream.close()
        }

        // Load CA certificate from res/raw (replace with your actual CA cert filename)
        val caInputStream: InputStream = context.resources.openRawResource(R.raw.swish_ca_certificate) // Your CA certificate
        val caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null) // Create an empty KeyStore
            setCertificateEntry("ca", CertificateFactory.getInstance("X.509").generateCertificate(caInputStream))
        }
        caInputStream.close()

        // Initialize TrustManager with the CA certificate
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(caKeyStore)

        // Initialize KeyManager with the client certificate
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "123456".toCharArray()) // Your client certificate password

        // Create SSLContext with both KeyManager and TrustManager
        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        }

        val trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager

        // Build the OkHttpClient with SSL settings
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    } catch (e: Exception) {
        Log.e("SwishPayment", "Error initializing SSL context: ${e.message}", e)
        throw RuntimeException("Failed to create OkHttpClient with Swish certificate")
    }
}

fun sendSwishPaymentRequest(context: Context, swishNumber: String, amount: Double) {
    if (amount <= 0) {
        Log.e("SwishPayment", "Invalid payment amount: $amount")
        return
    }

    val client = createSwishOkHttpClient(context)
    val jsonPayload = """
        {
            "payeeAlias": "$swishNumber",
            "amount": "$amount",
            "currency": "SEK",
            "message": "Payment for Order",
            "callbackUrl": "https://holly-royal-teal.glitch.me/paymentcallback"
        }
    """.trimIndent()

    // JSON data for the Swish API request, with dynamic Swish number and amount

    val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url("https://holly-royal-teal.glitch.me/paymentcallback") // Use your callback URL here for testing
        .post(requestBody)
        .build()


    client.newCall(request).enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                Log.i("SwishPayment", "Payment request successful: ${response.body?.string()}")
            } else {
                Log.e("SwishPayment", "Payment request failed with status ${response.code}: ${response.body?.string()}")
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            Log.e("SwishPayment", "Payment request failed: ${e.message}", e)
        }
    })
}

// Main function to make the Swish payment request by querying Firestore for Swish number and total price
    fun makeSwishPaymentRequest(context: Context, username: String) {
        val db = FirebaseFirestore.getInstance()

        // Step 1: Retrieve user document to get the Swish number
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { userDocuments ->
                if (userDocuments.isEmpty) {
                    Log.e("SwishPayment", "User with username $username not found.")
                    return@addOnSuccessListener
                }

                val userDoc = userDocuments.documents[0]
                val swishNumber = userDoc.getString("swishNumber")
                if (swishNumber.isNullOrEmpty()) {
                    Log.e("SwishPayment", "Swish number is missing for user $username.")
                    return@addOnSuccessListener
                }

                // Step 2: Find the event created by this user
                db.collection("events")
                    .whereEqualTo("createdBy", username)
                    .get()
                    .addOnSuccessListener { eventDocuments ->
                        if (eventDocuments.isEmpty) {
                            Log.e("SwishPayment", "No event found for user $username.")
                            return@addOnSuccessListener
                        }

                        val eventDoc = eventDocuments.documents[0]
                        val eventId = eventDoc.id

                        // Step 3: Retrieve the order associated with the event to get the total price
                        db.collection("orders")
                            .whereEqualTo("eventId", eventId)
                            .get()
                            .addOnSuccessListener { orderDocuments ->
                                if (orderDocuments.isEmpty) {
                                    Log.e("SwishPayment", "No order found for event ID $eventId.")
                                    return@addOnSuccessListener
                                }

                                val orderDoc = orderDocuments.documents[0]
                                val totalPrice = orderDoc.getDouble("totalPrice") ?: 0.0

                                // Proceed to create the Swish payment request with the retrieved data
                                sendSwishPaymentRequest(context, swishNumber, totalPrice)
                            }
                            .addOnFailureListener { e ->
                                Log.e("SwishPayment", "Error retrieving order: ${e.message}", e)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("SwishPayment", "Error retrieving event: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SwishPayment", "Error retrieving user: ${e.message}", e)
            }
    }


    /*fun createSwishOkHttpClient(context: Context): OkHttpClient {
       val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
           override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
           override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
           override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
       })

       val sslContext = SSLContext.getInstance("SSL").apply {
           init(null, trustAllCerts, java.security.SecureRandom())
       }

       return OkHttpClient.Builder()
           .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
           .hostnameVerifier { _, _ -> true }
           .build()
   }*/




    /*// Function to make the Swish payment request
    fun makeSwishPaymentRequest(context: Context) {
        try {
            Log.d("SwishPaymentRequest", "Creating OkHttpClient")
            val client = createSwishOkHttpClient(context)

            // JSON data for the payment request
            val jsonPayload = """
                {
                    "payeeAlias": "1231181189",
                    "amount": "100",
                    "currency": "SEK",
                    "message": "Payment for Order #12345",
                    "callbackUrl": "https://holly-royal-teal.glitch.me/paymentcallback"
                }
            """.trimIndent()

            // Convert JSON to RequestBody
            val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
            Log.d("SwishPaymentRequest", "Request Body: $jsonPayload")

            // Build the request with the JSON body
            val request = Request.Builder()
                .url("https://mss.cpc.getswish.net/swish-cpcapi/paymentrequests")  // Swish API URL
                .post(requestBody)
                .build()

            Log.d("SwishPaymentRequest", "Making payment request")

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onResponse(call: okhttp3.Call, response: Response) {
                    Log.d("SwishPaymentRequest", "Response received")
                    if (response.isSuccessful) {
                        // Handle success response
                        val responseBody = response.body?.string()
                        Log.i("SwishPayment", "Payment request successful: $responseBody")
                    } else {
                        // Handle error response
                        val errorBody = response.body?.string()
                        Log.e("SwishPayment", "Payment request failed with status ${response.code}: $errorBody")
                    }
                }

                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    // Handle failure (e.g., network error)
                    Log.e("SwishPayment", "Payment request failed: ${e.message}", e)
                }
            })
        } catch (e: Exception) {
            Log.e("SwishPayment", "Error in making payment request: ${e.message}", e)
        }
    }
    */