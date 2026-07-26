package org.example.infrastructure.http;

import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Доверие сертификатам как из JDK cacerts, так и из системного хранилища Windows-ROOT.
 * Нужно в корпоративных сетях с TLS-инспекцией (прокси подписывает свои сертификаты
 * корневым CA, установленным в Windows, но отсутствующим в JDK).
 * <p>
 * Живёт в общем пакете, а не рядом с клиентом MOEX: за внешними данными приложение ходит
 * не только в MOEX (ещё и в ЦБ за ключевой ставкой), и TLS-инспекция ломает их одинаково.
 */
public final class CompositeTrust {
    private static final Logger logger = LoggerFactory.getLogger(CompositeTrust.class);

    private CompositeTrust() {
    }

    public static void apply(OkHttpClient.Builder builder) {
        try {
            List<X509TrustManager> managers = new ArrayList<>();
            managers.add(trustManager(null));                 // JDK cacerts
            X509TrustManager windows = windowsRootManager();  // хранилище Windows
            if (windows != null) {
                managers.add(windows);
            }

            X509TrustManager composite = new CompositeX509TrustManager(managers);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{composite}, null);
            builder.sslSocketFactory(ctx.getSocketFactory(), composite);
        } catch (Exception e) {
            logger.warn("Не удалось настроить составное доверие TLS, используется JDK по умолчанию: {}",
                    e.getMessage());
        }
    }

    private static X509TrustManager windowsRootManager() {
        try {
            KeyStore ks = KeyStore.getInstance("Windows-ROOT");
            ks.load(null, null);
            return trustManager(ks);
        } catch (Exception e) {
            return null; // не Windows или хранилище недоступно
        }
    }

    private static X509TrustManager trustManager(KeyStore ks) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new IllegalStateException("X509TrustManager не найден");
    }

    /** Доверяет сертификату, если его принимает хотя бы один из делегатов. */
    private static final class CompositeX509TrustManager implements X509TrustManager {
        private final List<X509TrustManager> delegates;

        CompositeX509TrustManager(List<X509TrustManager> delegates) {
            this.delegates = delegates;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException last = null;
            for (X509TrustManager tm : delegates) {
                try {
                    tm.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    last = e;
                }
            }
            throw last != null ? last : new CertificateException("Нет доверенных менеджеров");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            for (X509TrustManager tm : delegates) {
                try {
                    tm.checkClientTrusted(chain, authType);
                    return;
                } catch (CertificateException ignored) {
                    // пробуем следующий
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<>();
            for (X509TrustManager tm : delegates) {
                issuers.addAll(List.of(tm.getAcceptedIssuers()));
            }
            return issuers.toArray(new X509Certificate[0]);
        }
    }
}
