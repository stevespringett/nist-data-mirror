/*
 * This file is part of nist-data-mirror.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package us.springett.nistdatamirror;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * This self-contained class can be called from the command-line. It downloads the
 * contents of NVD CPE/CVE XML and JSON data to the specified output path.
 *
 * @author Steve Springett (steve.springett@owasp.org)
 */
public class NistDataMirror {

    private static final String CVE_XML_12_MODIFIED_URL = "https://nvd.nist.gov/download/nvdcve-Modified.xml.gz";
    private static final String CVE_XML_20_MODIFIED_URL = "https://nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-Modified.xml.gz";
    private static final String CVE_XML_12_BASE_URL = "https://nvd.nist.gov/download/nvdcve-%d.xml.gz";
    private static final String CVE_XML_20_BASE_URL = "https://nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-%d.xml.gz";
    private static final String CVE_JSON_10_MODIFIED_URL = "https://static.nvd.nist.gov/feeds/json/cve/1.0/nvdcve-1.0-modified.json.gz";
    private static final String CVE_JSON_10_BASE_URL = "https://static.nvd.nist.gov/feeds/json/cve/1.0/nvdcve-1.0-%d.json.gz";
    private static final int START_YEAR = 2002;
    private static final int END_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    
    private final File outputDir;
    private final Proxy proxy;
    
    private boolean downloadFailed = false;

    public static void main (String[] args) {
        // Ensure at least one argument was specified
        if (args.length == 0) {
            System.out.println("Usage: java [-proxy <host:port>] <NistDataMirror outputDir>");
            return;
        }
        
        NistDataMirror nvd = new NistDataMirror(args);
        nvd.mirror();
        if (nvd.downloadFailed) {
          System.exit(1);
        }
    }
    	
    public NistDataMirror(String[] args) {
    	String outputDirPath = args[0];
        
        if (args[0].equals("-proxy")) {
        	this.proxy = createProxy(args[1]);
        	outputDirPath = args[2];
        } else {
        	this.proxy = Proxy.NO_PROXY;
        }
        
        this.outputDir = new File(outputDirPath);
        if ( ! outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    public void mirror() {
    	// if a proxy is in use, handle case where proxy terminates ssl (many corp proxies do)
    	// FIXME - should trust all certs be an extra parameter???
    	if (!proxy.equals(Proxy.NO_PROXY)) {
    		initSSLTrustAllManager();
    	}
    	
        Date currentDate = new Date();
        System.out.println("Downloading files at " + currentDate);

        doDownload(CVE_XML_12_MODIFIED_URL);
        doDownload(CVE_XML_20_MODIFIED_URL);
        doDownload(CVE_JSON_10_MODIFIED_URL);
        for (int i=START_YEAR; i<=END_YEAR; i++) {
            String cve12BaseUrl = CVE_XML_12_BASE_URL.replace("%d", String.valueOf(i));
            String cve20BaseUrl = CVE_XML_20_BASE_URL.replace("%d", String.valueOf(i));
            String cveJsonBaseUrl = CVE_JSON_10_BASE_URL.replace("%d", String.valueOf(i));
            doDownload(cve12BaseUrl);
            doDownload(cve20BaseUrl);
            doDownload(cveJsonBaseUrl);
        }
    }

    private Proxy createProxy(String proxy) {
    	int indexOf = proxy.indexOf(":");
    	if (indexOf != -1) {
    		String host = proxy.substring(0, indexOf);
    		int port = toPort(proxy.substring(indexOf + 1));
    		return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    	} else {
    		return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy, 3128));
    	}
    }
    
    private int toPort(String port) {
    	try {
    		return Integer.parseInt(port);
    	} catch (NumberFormatException nfe) {
    		return 3128;
    	}
    }

    private long checkHead(String cveUrl) {
        try {
            URL url = new URL(cveUrl);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection(proxy);
            connection.setRequestMethod("HEAD");
            connection.connect();
            connection.getInputStream();
            return connection.getContentLengthLong();
        } catch (IOException e) {
            System.out.println("Failed to determine content length");
        }
        return 0;
    }

    private void doDownload(String cveUrl) {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        File file = null;
        boolean success = false;
        try {
            URL url = new URL(cveUrl);
            String filename = url.getFile();
            filename = filename.substring(filename.lastIndexOf('/') + 1);
            file = new File(outputDir, filename).getAbsoluteFile();

            if (file.exists()) {
                long fileSize = checkHead(cveUrl);
                if (file.length() == fileSize) {
                    System.out.println("Using cached version of " + filename);
                    return;
                }
            }

            URLConnection connection = url.openConnection(proxy);
            System.out.println("Downloading " + url.toExternalForm());
            bis = new BufferedInputStream(connection.getInputStream());
            file = new File(outputDir, filename);
            bos = new BufferedOutputStream(new FileOutputStream(file));

            int i;
            while ((i = bis.read()) != -1) {
                bos.write(i);
            }
            success = true;
        } catch (IOException e) {
            System.out.println("Download failed : " + e.getLocalizedMessage());
            downloadFailed = true;
        } finally {
            close(bis);
            close(bos);
        }
        if (file != null && success)
            uncompress(file);
    }

    private void uncompress(File file) {
        byte[] buffer = new byte[1024];
        GZIPInputStream gzis = null;
        FileOutputStream out = null;
        try{
            System.out.println("Uncompressing " + file.getName());
            gzis = new GZIPInputStream(new FileInputStream(file));
            out = new FileOutputStream(new File(file.getAbsolutePath().replaceAll(".gz", "")));
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }catch(IOException ex){
            ex.printStackTrace();
        } finally {
            close(gzis);
            close(out);
        }
    }

    private void close (Closeable object) {
        if (object != null) {
            try {
                object.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
 	private void initSSLTrustAllManager() {
 		TrustManager[] trustAllCerts = new TrustManager[] { 
 			new X509TrustManager() {
	 			/**
	 			 * Trust all certs
	 			 */
	 			public X509Certificate[] getAcceptedIssuers() {
	 				return null;
	 			}
	
	 			/**
	 			 * Trust all certs
	 			 */
	 			public void checkClientTrusted(X509Certificate[] certs, String authType) {
	 			}
	
	 			/**
	 			 * Trust all certs
	 			 */
	 			public void checkServerTrusted(X509Certificate[] certs, String authType) {
	 			}
	 		} 
 		};

 		HostnameVerifier allHostsValid = new HostnameVerifier() {
 			public boolean verify(String hostname, SSLSession session) {
 				return true;
 			}
 		};

 		try {
 			SSLContext sc = SSLContext.getInstance("TLSv1.2");
 			sc.init(null, trustAllCerts, new java.security.SecureRandom());
 			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
 			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
 		} catch (Exception e) {
 			e.printStackTrace();
 		}
 	}
}
