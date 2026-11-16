package com.lge.lgmdmservice;
import android.content.Context;
import android.os.Build;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.*;
import javax.net.ssl.*;

public final class LegacyTlsCompat {
 private static volatile SSLSocketFactory factory;
 private LegacyTlsCompat(){}
 public static void configure(Context context,HttpsURLConnection connection)throws Exception{
  if(Build.VERSION.SDK_INT>23)return;
  if(factory==null)synchronized(LegacyTlsCompat.class){if(factory==null)factory=build(context.getApplicationContext());}
  connection.setSSLSocketFactory(factory);
 }
 private static SSLSocketFactory build(Context context)throws Exception{
  X509TrustManager system=manager(null);
  KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);
  CertificateFactory cf=CertificateFactory.getInstance("X.509");
  try(InputStream in=context.getResources().openRawResource(R.raw.isrg_root_x1)){store.setCertificateEntry("isrg-root-x1",cf.generateCertificate(in));}
  X509TrustManager extra=manager(store);
  X509TrustManager combined=new X509TrustManager(){
   public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{system.checkClientTrusted(c,a);}
   public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{try{system.checkServerTrusted(c,a);}catch(CertificateException original){if(!missingAnchor(original))throw original;try{extra.checkServerTrusted(c,a);}catch(CertificateException fallback){fallback.addSuppressed(original);throw fallback;}}}
   public X509Certificate[] getAcceptedIssuers(){X509Certificate[] a=system.getAcceptedIssuers(),b=extra.getAcceptedIssuers(),all=new X509Certificate[a.length+b.length];System.arraycopy(a,0,all,0,a.length);System.arraycopy(b,0,all,a.length,b.length);return all;}
  };
  SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,new TrustManager[]{combined},null);return ssl.getSocketFactory();
 }
 private static X509TrustManager manager(KeyStore store)throws Exception{TrustManagerFactory f=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());f.init(store);for(TrustManager t:f.getTrustManagers())if(t instanceof X509TrustManager)return(X509TrustManager)t;throw new CertificateException("No X509 trust manager");}
 private static boolean missingAnchor(Throwable t){for(;t!=null;t=t.getCause()){String m=t.getMessage();if(m!=null&&m.contains("Trust anchor for certification path not found"))return true;}return false;}
}