package mozilla.components.browser.engine.system

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlinx.coroutines.experimental.runBlocking
import mozilla.components.browser.engine.system.matcher.UrlMatcher
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.support.test.mock
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBitmap
import java.lang.reflect.Modifier

@RunWith(RobolectricTestRunner::class)
class SystemEngineSessionTest {

    @Test
    fun testWebChromeClientNotifiesObservers() {
        val engineSession = SystemEngineSession()
        val engineView = SystemEngineView(RuntimeEnvironment.application)
        engineView.render(engineSession)

        var observedProgress = 0
        engineSession.register(object : EngineSession.Observer {
            override fun onProgress(progress: Int) { observedProgress = progress }
        })

        engineView.currentWebView.webChromeClient.onProgressChanged(null, 100)
        Assert.assertEquals(100, observedProgress)
    }

    @Test
    fun testLoadUrl() {
        var loadedUrl: String? = null
        var loadHeaders: Map<String, String>? = null

        val engineSession = spy(SystemEngineSession())
        val webView = spy(object : WebView(RuntimeEnvironment.application) {
            override fun loadUrl(url: String?, additionalHttpHeaders: MutableMap<String, String>?) {
                loadedUrl = url
                loadHeaders = additionalHttpHeaders
            }
        })

        engineSession.loadUrl("")
        verify(webView, never()).loadUrl(anyString())

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.loadUrl("http://mozilla.org")
        verify(webView).loadUrl(eq("http://mozilla.org"), any())

        assertEquals("http://mozilla.org", loadedUrl)

        assertNotNull(loadHeaders)
        assertEquals(1, loadHeaders!!.size)
        assertTrue(loadHeaders!!.containsKey("X-Requested-With"))
        assertEquals("", loadHeaders!!["X-Requested-With"])
    }

    @Test
    fun testLoadData() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)

        engineSession.loadData("<html><body>Hello!</body></html>")
        verify(webView, never()).loadData(anyString(), eq("text/html"), eq("UTF-8"))

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.loadData("<html><body>Hello!</body></html>")
        verify(webView).loadData(eq("<html><body>Hello!</body></html>"), eq("text/html"), eq("UTF-8"))

        engineSession.loadData("Hello!", "text/plain", "UTF-8")
        verify(webView).loadData(eq("Hello!"), eq("text/plain"), eq("UTF-8"))

        engineSession.loadData("ahr0cdovl21vemlsbgeub3jn==", "text/plain", "base64")
        verify(webView).loadData(eq("ahr0cdovl21vemlsbgeub3jn=="), eq("text/plain"), eq("base64"))
    }

    @Test
    fun testStopLoading() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)

        engineSession.stopLoading()
        verify(webView, never()).stopLoading()

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.stopLoading()
        verify(webView).stopLoading()
    }

    @Test
    fun testReload() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)

        engineSession.reload()
        verify(webView, never()).reload()

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.reload()
        verify(webView).reload()
    }

    @Test
    fun testGoBack() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)

        engineSession.goBack()
        verify(webView, never()).goBack()

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.goBack()
        verify(webView).goBack()
    }

    @Test
    fun testGoForward() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)

        engineSession.goForward()
        verify(webView, never()).goForward()

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.goForward()
        verify(webView).goForward()
    }

    @Test
    fun testSaveState() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)

        engineSession.saveState()
        verify(webView, never()).saveState(any(Bundle::class.java))

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.saveState()
        verify(webView).saveState(any(Bundle::class.java))
    }

    @Test
    fun testRestoreState() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)

        engineSession.restoreState(emptyMap())
        verify(webView, never()).restoreState(any(Bundle::class.java))

        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.restoreState(emptyMap())
        verify(webView).restoreState(any(Bundle::class.java))
    }

    @Test
    fun testEnableTrackingProtection() {
        SystemEngineView.URL_MATCHER = UrlMatcher(arrayOf(""))

        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)
        `when`(webView.context).thenReturn(RuntimeEnvironment.application)
        `when`(engineSession.currentView()).thenReturn(webView)

        var enabledObserved: Boolean? = null
        engineSession.register(object : EngineSession.Observer {
            override fun onTrackerBlockingEnabledChange(enabled: Boolean) {
                enabledObserved = enabled
            }
        })

        assertFalse(engineSession.trackingProtectionEnabled)
        runBlocking { engineSession.enableTrackingProtection() }
        assertTrue(engineSession.trackingProtectionEnabled)
        assertNotNull(enabledObserved)
        assertTrue(enabledObserved!!)
    }

    @Test
    fun testDisableTrackingProtection() {
        val engineSession = spy(SystemEngineSession())
        var enabledObserved: Boolean? = null
        engineSession.register(object : EngineSession.Observer {
            override fun onTrackerBlockingEnabledChange(enabled: Boolean) {
                enabledObserved = enabled
            }
        })

        engineSession.trackingProtectionEnabled = true

        engineSession.disableTrackingProtection()
        assertFalse(engineSession.trackingProtectionEnabled)
        assertNotNull(enabledObserved)
        assertFalse(enabledObserved!!)
    }

    @Test
    fun testSettings() {
        assertEquals(DefaultSettings(), spy(SystemEngineSession()).settings)

        val defaultSettings = DefaultSettings(trackingProtectionPolicy = EngineSession.TrackingProtectionPolicy.all())
        assertSame(defaultSettings, spy(SystemEngineSession(defaultSettings)).settings)

        val engineSession = spy(SystemEngineSession(defaultSettings))
        val webView = mock(WebView::class.java)
        val webViewSettings = mock(WebSettings::class.java)
        `when`(webView.context).thenReturn(RuntimeEnvironment.application)
        `when`(engineSession.currentView()).thenReturn(webView)
        `when`(webView.settings).thenReturn(webViewSettings)
        engineSession.initSettings()
        assertNotSame(defaultSettings, engineSession.settings)
        assertNotEquals(DefaultSettings(), engineSession.settings)
    }

    @Test
    fun testInitSettings() {
        val engineSession = spy(SystemEngineSession())
        val webViewSettings = mock(WebSettings::class.java)
        `when`(webViewSettings.displayZoomControls).thenReturn(true)
        `when`(webViewSettings.allowContentAccess).thenReturn(true)
        `when`(webViewSettings.allowFileAccess).thenReturn(true)

        val webView = mock(WebView::class.java)
        `when`(webView.context).thenReturn(RuntimeEnvironment.application)
        `when`(webView.settings).thenReturn(webViewSettings)
        `when`(webView.isVerticalScrollBarEnabled).thenReturn(true)
        `when`(webView.isHorizontalScrollBarEnabled).thenReturn(true)

        try {
            engineSession.initSettings()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) { }

        `when`(engineSession.currentView()).thenReturn(webView)
        engineSession.initSettings()

        assertFalse(engineSession.settings.javascriptEnabled)
        engineSession.settings.javascriptEnabled = true
        verify(webViewSettings).javaScriptEnabled = true

        assertFalse(engineSession.settings.domStorageEnabled)
        engineSession.settings.domStorageEnabled = true
        verify(webViewSettings).domStorageEnabled = true

        assertNull(engineSession.settings.userAgentString)
        engineSession.settings.userAgentString = "userAgent"
        verify(webViewSettings).userAgentString = "userAgent"

        assertFalse(engineSession.settings.mediaPlaybackRequiresUserGesture)
        engineSession.settings.mediaPlaybackRequiresUserGesture = false
        verify(webViewSettings).mediaPlaybackRequiresUserGesture = false

        assertFalse(engineSession.settings.javaScriptCanOpenWindowsAutomatically)
        engineSession.settings.javaScriptCanOpenWindowsAutomatically = true
        verify(webViewSettings).javaScriptCanOpenWindowsAutomatically = true

        assertTrue(engineSession.settings.displayZoomControls)
        engineSession.settings.javaScriptCanOpenWindowsAutomatically = false
        verify(webViewSettings).javaScriptCanOpenWindowsAutomatically = false

        assertFalse(engineSession.settings.loadWithOverviewMode)
        engineSession.settings.loadWithOverviewMode = true
        verify(webViewSettings).loadWithOverviewMode = true

        assertTrue(engineSession.settings.allowContentAccess)
        engineSession.settings.allowContentAccess = false
        verify(webViewSettings).allowContentAccess = false

        assertTrue(engineSession.settings.allowFileAccess)
        engineSession.settings.allowFileAccess = false
        verify(webViewSettings).allowFileAccess = false

        assertFalse(engineSession.settings.allowUniversalAccessFromFileURLs)
        engineSession.settings.allowUniversalAccessFromFileURLs = true
        verify(webViewSettings).allowUniversalAccessFromFileURLs = true

        assertFalse(engineSession.settings.allowFileAccessFromFileURLs)
        engineSession.settings.allowFileAccessFromFileURLs = true
        verify(webViewSettings).allowFileAccessFromFileURLs = true

        assertTrue(engineSession.settings.verticalScrollBarEnabled)
        engineSession.settings.verticalScrollBarEnabled = false
        verify(webView).isVerticalScrollBarEnabled = false

        assertTrue(engineSession.settings.horizontalScrollBarEnabled)
        engineSession.settings.horizontalScrollBarEnabled = false
        verify(webView).isHorizontalScrollBarEnabled = false

        assertTrue(engineSession.webFontsEnabled)
        assertTrue(engineSession.settings.webFontsEnabled)
        engineSession.settings.webFontsEnabled = false
        assertFalse(engineSession.webFontsEnabled)
        assertFalse(engineSession.settings.webFontsEnabled)

        assertEquals(EngineSession.TrackingProtectionPolicy.none(), engineSession.settings.trackingProtectionPolicy)
        engineSession.settings.trackingProtectionPolicy = EngineSession.TrackingProtectionPolicy.all()
        verify(engineSession).enableTrackingProtection(EngineSession.TrackingProtectionPolicy.all())

        engineSession.settings.trackingProtectionPolicy = null
        verify(engineSession).disableTrackingProtection()

        verify(webViewSettings).setAppCacheEnabled(false)
        verify(webViewSettings).setGeolocationEnabled(false)
        verify(webViewSettings).databaseEnabled = false
        verify(webViewSettings).savePassword = false
        verify(webViewSettings).saveFormData = false
        verify(webViewSettings).builtInZoomControls = true
    }

    @Test
    fun testDefaultSettings() {
        val defaultSettings = DefaultSettings(
                javascriptEnabled = false,
                domStorageEnabled = false,
                webFontsEnabled = false,
                trackingProtectionPolicy = EngineSession.TrackingProtectionPolicy.all(),
                userAgentString = "userAgent",
                mediaPlaybackRequiresUserGesture = false,
                javaScriptCanOpenWindowsAutomatically = true,
                displayZoomControls = false,
                loadWithOverviewMode = true)
        val engineSession = spy(SystemEngineSession(defaultSettings))
        val webView = mock(WebView::class.java)
        `when`(webView.context).thenReturn(RuntimeEnvironment.application)
        `when`(engineSession.currentView()).thenReturn(webView)

        val webViewSettings = mock(WebSettings::class.java)
        `when`(webView.settings).thenReturn(webViewSettings)

        engineSession.initSettings()
        verify(webViewSettings).domStorageEnabled = false
        verify(webViewSettings).javaScriptEnabled = false
        verify(webViewSettings).userAgentString = "userAgent"
        verify(webViewSettings).mediaPlaybackRequiresUserGesture = false
        verify(webViewSettings).javaScriptCanOpenWindowsAutomatically = true
        verify(webViewSettings).displayZoomControls = false
        verify(webViewSettings).loadWithOverviewMode = true
        verify(engineSession).enableTrackingProtection(EngineSession.TrackingProtectionPolicy.all())
        assertFalse(engineSession.webFontsEnabled)
    }

    @Test
    fun testSharedFieldsAreVolatile() {
        val settingsField = SystemEngineSession::class.java.getDeclaredField("internalSettings")
        val webFontsEnabledField = SystemEngineSession::class.java.getDeclaredField("webFontsEnabled")
        val trackingProtectionField = SystemEngineSession::class.java.getDeclaredField("trackingProtectionEnabled")

        assertTrue(Modifier.isVolatile(settingsField.modifiers))
        assertTrue(Modifier.isVolatile(webFontsEnabledField.modifiers))
        assertTrue(Modifier.isVolatile(trackingProtectionField.modifiers))
    }

    @Test
    fun testSettingRequestInterceptor() {
        var interceptorCalledWithUri: String? = null

        val interceptor = object : RequestInterceptor {
            override fun onLoadRequest(session: EngineSession, uri: String): RequestInterceptor.InterceptionResponse? {
                interceptorCalledWithUri = uri
                return RequestInterceptor.InterceptionResponse("<h1>Hello World</h1>")
            }
        }

        val defaultSettings = DefaultSettings(requestInterceptor = interceptor)

        val engineSession = SystemEngineSession(defaultSettings)
        val engineView = SystemEngineView(RuntimeEnvironment.application)
        engineView.currentWebView = spy(engineView.currentWebView)
        engineView.render(engineSession)

        val request: WebResourceRequest = mock()
        doReturn(Uri.parse("sample:about")).`when`(request).url

        val response = engineView.currentWebView.webViewClient.shouldInterceptRequest(
            engineView.currentWebView,
            request)

        assertEquals("sample:about", interceptorCalledWithUri)

        assertNotNull(response)

        assertEquals("<h1>Hello World</h1>", response.data.bufferedReader().use { it.readText() })
        assertEquals("text/html", response.mimeType)
        assertEquals("UTF-8", response.encoding)
    }

    @Test
    fun testOnLoadRequestWithoutInterceptor() {
        val defaultSettings = DefaultSettings()

        val engineSession = SystemEngineSession(defaultSettings)
        val engineView = SystemEngineView(RuntimeEnvironment.application)
        engineView.currentWebView = spy(engineView.currentWebView)
        engineView.render(engineSession)

        val request: WebResourceRequest = mock()
        doReturn(Uri.parse("sample:about")).`when`(request).url

        val response = engineView.currentWebView.webViewClient.shouldInterceptRequest(
            engineView.currentWebView,
            request)

        assertNull(response)
    }

    @Test
    fun testOnLoadRequestWithInterceptorThatDoesNotIntercept() {
        var interceptorCalledWithUri: String? = null

        val interceptor = object : RequestInterceptor {
            override fun onLoadRequest(session: EngineSession, uri: String): RequestInterceptor.InterceptionResponse? {
                interceptorCalledWithUri = uri
                return null
            }
        }

        val defaultSettings = DefaultSettings(requestInterceptor = interceptor)

        val engineSession = SystemEngineSession(defaultSettings)
        val engineView = SystemEngineView(RuntimeEnvironment.application)
        engineView.currentWebView = spy(engineView.currentWebView)
        engineView.render(engineSession)

        val request: WebResourceRequest = mock()
        doReturn(Uri.parse("sample:about")).`when`(request).url

        val response = engineView.currentWebView.webViewClient.shouldInterceptRequest(
            engineView.currentWebView,
            request)

        assertEquals("sample:about", interceptorCalledWithUri)

        assertNull(response)
    }

    @Test
    fun `desktop mode`() {
        val userAgentMobile = "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Mobile Safari/537.36"
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)
        val webViewSettings = mock(WebSettings::class.java)

        engineSession.toggleDesktopMode(true)
        verify(engineSession).currentView()
        verify(webView, never()).settings

        `when`(engineSession.currentView()).thenReturn(webView)
        `when`(webView.settings).thenReturn(webViewSettings)
        `when`(webViewSettings.userAgentString).thenReturn(userAgentMobile)

        engineSession.toggleDesktopMode(true)
        verify(webViewSettings).useWideViewPort = true
        verify(engineSession).toggleDesktopUA(userAgentMobile, true)

        engineSession.toggleDesktopMode(true)
        verify(webView, never()).reload()

        engineSession.toggleDesktopMode(true, true)
        verify(webView).reload()
    }

    @Test
    fun testDesktopModeUA() {
        val userAgentMobile = "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Mobile Safari/537.36"
        val userAgentDesktop = "Mozilla/5.0 (Linux; diordnA 9) AppleWebKit/537.36 eliboM Safari/537.36"
        val engineSession = spy(SystemEngineSession())

        assertEquals(engineSession.toggleDesktopUA(userAgentMobile, false), userAgentMobile)
        assertEquals(engineSession.toggleDesktopUA(userAgentMobile, true), userAgentDesktop)
    }

    @Test
    fun testFindAll() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)
        engineSession.findAll("mozilla")
        verify(webView, never()).findAllAsync(anyString())

        `when`(engineSession.currentView()).thenReturn(webView)
        var findObserved: String? = null
        engineSession.register(object : EngineSession.Observer {
            override fun onFind(text: String) {
                findObserved = text
            }
        })
        engineSession.findAll("mozilla")
        verify(webView).findAllAsync("mozilla")
        assertEquals("mozilla", findObserved)
    }

    @Test
    fun testFindNext() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)
        engineSession.findNext(true)
        verify(webView, never()).findNext(any(Boolean::class.java))

        `when`(engineSession.currentView()).thenReturn(webView)
        engineSession.findNext(true)
        verify(webView).findNext(true)
    }

    @Test
    fun testClearFindMatches() {
        val engineSession = spy(SystemEngineSession())
        val webView = mock(WebView::class.java)
        engineSession.clearFindMatches()
        verify(webView, never()).clearMatches()

        `when`(engineSession.currentView()).thenReturn(webView)
        engineSession.clearFindMatches()
        verify(webView).clearMatches()
    }

    @Test
    fun testClearDataMakingExpectedCalls() {
        val engineSession = spy(SystemEngineSession::class.java)
        val webView = mock(WebView::class.java)
        val webStorage: WebStorage = mock()
        val webViewDatabase: WebViewDatabase = mock()
        val context: Context = RuntimeEnvironment.application

        engineSession.clearData()
        verify(webView, never()).clearFormData()
        verify(webView, never()).clearHistory()
        verify(webView, never()).clearMatches()
        verify(webView, never()).clearSslPreferences()
        verify(webView, never()).clearCache(true)

        doReturn(webStorage).`when`(engineSession).webStorage()
        doReturn(webViewDatabase).`when`(engineSession).webViewDatabase(context)
        `when`(webView.context).thenReturn(context)
        `when`(engineSession.currentView()).thenReturn(webView)

        engineSession.clearData()
        verify(webView).clearFormData()
        verify(webView).clearHistory()
        verify(webView).clearMatches()
        verify(webView).clearSslPreferences()
        verify(webView).clearCache(true)
        verify(webStorage).deleteAllData()
        verify(webViewDatabase).clearHttpAuthUsernamePassword()
    }

    @Test
    fun testCaptureThumbnail() {
        val engineSession = spy(SystemEngineSession::class.java)
        val webView = mock(WebView::class.java)
        `when`(engineSession.currentView()).thenReturn(webView)
        assertNull(engineSession.captureThumbnail())

        `when`(webView.drawingCache).thenReturn(ShadowBitmap.createBitmap(10, 10, Bitmap.Config.RGB_565))
        assertNotNull(engineSession.captureThumbnail())
    }
}