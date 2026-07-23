package eu.heha.conifer.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebDavMultistatusTest {

    @Test
    fun parsesAFolderListingWithFilesAndSubfolders() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/user/MyApp/.sync/posts/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getetag>&quot;self-etag&quot;</d:getetag>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/user/MyApp/.sync/posts/2026-07/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getetag>&quot;month-etag&quot;</d:getetag>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/user/MyApp/.sync/posts/2026-07/some-post.json</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getetag>&quot;post-etag&quot;</d:getetag>
                    <d:resourcetype/>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = parseMultistatus(xml)

        assertEquals(3, entries.size)
        val self = entries[0]
        assertEquals("self-etag", self.etag)
        assertTrue(self.isCollection)

        val month = entries[1]
        assertEquals("month-etag", month.etag)
        assertTrue(month.isCollection)
        assertTrue(month.href.endsWith("/2026-07/"))

        val post = entries[2]
        assertEquals("post-etag", post.etag)
        assertFalse(post.isCollection)
        assertTrue(post.href.endsWith("/some-post.json"))
    }

    @Test
    fun namespacePrefixIsIgnored() {
        val xml = """
            <?xml version="1.0"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/remote.php/dav/files/user/file.json</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getetag>"prefixed-etag"</D:getetag>
                    <D:resourcetype/>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = parseMultistatus(xml)

        assertEquals(1, entries.size)
        assertEquals("prefixed-etag", entries[0].etag)
    }

    @Test
    fun propstatsWithNon200StatusAreIgnored() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/user/file.json</d:href>
                <d:propstat>
                  <d:prop><d:getetag>"real-etag"</d:getetag></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop><d:quota-used-bytes/></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = parseMultistatus(xml)

        assertEquals(1, entries.size)
        assertEquals("real-etag", entries[0].etag)
    }

    @Test
    fun normalizeEtagStripsQuotesAndWeakPrefix() {
        assertEquals("abc123", normalizeEtag("\"abc123\""))
        assertEquals("abc123", normalizeEtag("W/\"abc123\""))
        assertEquals("abc123", normalizeEtag("abc123"))
    }

    @Test
    fun hrefPercentEncodingIsDecoded() {
        val xml = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/user/MyApp/2026-07/2026-07-04%20copy.md</d:href>
                <d:propstat>
                  <d:prop><d:getetag>"e"</d:getetag><d:resourcetype/></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = parseMultistatus(xml)

        assertEquals("/remote.php/dav/files/user/MyApp/2026-07/2026-07-04 copy.md", entries[0].href)
    }
}
