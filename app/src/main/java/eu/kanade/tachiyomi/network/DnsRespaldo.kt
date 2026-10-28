package eu.kanade.tachiyomi.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS del sistema y, si no resuelve (operadoras o DNS privados que bloquean p. ej. raw.githubusercontent.com),
 * DNS sobre HTTPS de Cloudflare y luego de Google, con IPs fijas para no depender del DNS roto.
 */
object DnsRespaldo : Dns {
    private val base by lazy { OkHttpClient.Builder().build() }
    private val doh: List<Dns> by lazy {
        listOf(
            DnsOverHttps.Builder().client(base).url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1")).build(),
            DnsOverHttps.Builder().client(base).url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4")).build(),
        )
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val err = try { return Dns.SYSTEM.lookup(hostname) } catch (e: UnknownHostException) { e }
        for (d in doh) runCatching { d.lookup(hostname) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        throw err
    }
}
