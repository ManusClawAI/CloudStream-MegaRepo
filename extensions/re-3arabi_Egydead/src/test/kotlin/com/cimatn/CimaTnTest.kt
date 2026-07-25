import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl // 👈 استيراد الطريقة الجديدة
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.util.concurrent.TimeUnit

class Freex2lineTest {

    @Test
    fun runFreex2lineTest() {

        val cookieJar = object : CookieJar {
            private val cookieStore = mutableMapOf<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: listOf()
            }
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"

        println("\n===== STEP 1: GET REQUEST (INITIALIZE SESSION) =====")
        val headUrl = "https://rm.freex2line.online/loadon/?link=aHR0cHM6Ly9jaW1hbm93LmNjLyVkOSU4NSVkOCViMyVkOSU4NCVkOCViMyVkOSU4NC10aGUtYmFkLWd1eXMtYnJlYWtpbmctaW4tJWQ4JWFjMi0lZDglYWQxLSVkOSU4NSVkOCVhZiVkOCVhOCVkOSU4NCVkOCVhYyVkOCVhOS93YXRjaGluZy8="

        val headRequest = Request.Builder()
            .url(headUrl)
            .header("User-Agent", userAgent)
            .build()

        val headResponse = client.newCall(headRequest).execute()

        val phpsessid = cookieJar.loadForRequest(headUrl.toHttpUrl()).find { it.name == "PHPSESSID" }?.value
        println("PHPSESSID: $phpsessid")

        println("\n===== STEP 2: GET PAGE (BLOG POST) =====")
        val pageUrl = "https://rm.freex2line.online/2020/02/blog-post.html/"

        val pageRequest = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", userAgent)
            .header("Referer", "https://rm.freex2line.online/")
            .build()

        val pageResponse = client.newCall(pageRequest).execute()
        val pageContent = pageResponse.body?.string() ?: ""

        println("\n===== STEP 3: EXTRACT TOKEN =====")
        val tokenMatch = Regex("""data-token="([^"]+)"""").find(pageContent)
        val token = tokenMatch?.groupValues?.get(1)

        if (token.isNullOrBlank()) {
            println("❌ TOKEN NOT FOUND")
            println("Page Preview: ${if(pageContent.length > 200) pageContent.take(200) else pageContent}")
            return
        }
        println("✅ TOKEN FOUND: $token")

        println("\n⏳ جاري الانتظار 10 ثواني لمحاكاة العداد الزمني...")
        Thread.sleep(10000)

        println("\n===== STEP 4: FINAL REQUEST =====")
        val apiUrl = "https://rm.freex2line.online/2020/02/blog-post.html/get-link.php?token=$token"

        val apiRequest = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", userAgent)
            .header("Referer", pageUrl)
            .build()

        val apiResponse = client.newCall(apiRequest).execute()
        println("\nStatus: ${apiResponse.code}")
        println("\n--- RESPONSE BODY ---")
        println(apiResponse.body?.string())
    }
}