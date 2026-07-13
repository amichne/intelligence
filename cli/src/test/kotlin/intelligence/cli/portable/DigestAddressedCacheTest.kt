package intelligence.cli.portable

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DigestAddressedCacheTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `default cache root prefers XDG and otherwise uses the user home`() {
        assertEquals(
            Path.of("/tmp/example-xdg/intelligence/sha256"),
            assertIs<DigestCacheRootResolution.Resolved>(
                DigestCacheRoot.resolve("/tmp/example-xdg", "/Users/example"),
            ).path,
        )
        assertEquals(
            Path.of("/Users/example/.cache/intelligence/sha256"),
            assertIs<DigestCacheRootResolution.Resolved>(
                DigestCacheRoot.resolve(null, "/Users/example"),
            ).path,
        )
        assertIs<DigestCacheRootResolution.Rejected>(DigestCacheRoot.resolve("relative", "/Users/example"))
        assertIs<DigestCacheRootResolution.Rejected>(DigestCacheRoot.resolve(null, null))
    }

    @Test
    fun `inserted blobs are immutable verified hits and returned bytes are copies`() {
        val cache = DigestAddressedCache.at(temporaryDirectory.resolve("cache"))
        val bytes = "exact cached bytes\n".encodeToByteArray()
        val expectation = CacheBlobExpectation.fromVerified(bytes)

        assertIs<DigestCacheInsertion.Stored>(cache.insert(expectation, bytes))
        assertIs<DigestCacheInsertion.AlreadyPresent>(cache.insert(expectation, bytes))
        val hit = assertIs<DigestCacheRead.Hit>(cache.read(expectation))
        val mutable = hit.blob.bytes()
        mutable.fill(0)

        assertContentEquals(bytes, assertIs<DigestCacheRead.Hit>(cache.read(expectation)).blob.bytes())
        assertEquals(cache.pathFor(expectation.sha256), hit.blob.path)
        assertEquals(1L, Files.list(hit.blob.path.parent).use { stream -> stream.count() })
    }

    @Test
    fun `missing and corrupt cache entries fail closed without repair`() {
        val cache = DigestAddressedCache.at(temporaryDirectory.resolve("cache"))
        val expected = "good".encodeToByteArray()
        val expectation = CacheBlobExpectation.fromVerified(expected)
        assertEquals(DigestCacheRead.Miss, cache.read(expectation))

        val target = cache.pathFor(expectation.sha256)
        Files.createDirectories(target.parent)
        Files.write(target, "evil".encodeToByteArray())

        val rejected = assertIs<DigestCacheRead.Rejected>(cache.read(expectation))
        assertIs<DigestCacheRejection.DigestMismatch>(rejected.reason)
        assertIs<DigestCacheInsertion.Rejected>(cache.insert(expectation, expected))
        assertContentEquals("evil".encodeToByteArray(), Files.readAllBytes(target))
    }

    @Test
    fun `symbolic cache entries are rejected without following them`() {
        val cache = DigestAddressedCache.at(temporaryDirectory.resolve("cache"))
        val expected = "good".encodeToByteArray()
        val expectation = CacheBlobExpectation.fromVerified(expected)
        val external = temporaryDirectory.resolve("external")
        Files.write(external, expected)
        val target = cache.pathFor(expectation.sha256)
        Files.createDirectories(target.parent)
        Files.createSymbolicLink(target, external)

        assertIs<DigestCacheRejection.NonRegularBlob>(
            assertIs<DigestCacheRead.Rejected>(cache.read(expectation)).reason,
        )
        assertIs<DigestCacheInsertion.Rejected>(cache.insert(expectation, expected))
        assertTrue(Files.isSymbolicLink(target))
        assertContentEquals(expected, Files.readAllBytes(external))
    }

    @Test
    fun `concurrent insertion converges on one exact immutable blob`() {
        val cache = DigestAddressedCache.at(temporaryDirectory.resolve("cache"))
        val bytes = ByteArray(64 * 1024) { index -> (index % 251).toByte() }
        val expectation = CacheBlobExpectation.fromVerified(bytes)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                executor.submit<DigestCacheInsertion> {
                    ready.countDown()
                    start.await()
                    cache.insert(expectation, bytes)
                }
            }
            ready.await()
            start.countDown()
            val results = futures.map { future -> future.get() }

            assertEquals(1, results.count { result -> result is DigestCacheInsertion.Stored })
            assertEquals(1, results.count { result -> result is DigestCacheInsertion.AlreadyPresent })
            assertContentEquals(bytes, assertIs<DigestCacheRead.Hit>(cache.read(expectation)).blob.bytes())
        } finally {
            executor.shutdownNow()
        }
    }
}
