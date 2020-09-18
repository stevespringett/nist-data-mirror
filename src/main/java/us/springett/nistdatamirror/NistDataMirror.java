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
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * This self-contained class can be called from the command-line. It downloads
 * the contents of NVD CPE/CVE JSON data to the specified output path.
 *
 * @author Steve Springett (steve.springett@owasp.org)
 */
public class NistDataMirror {

    private static final String CVE_JSON_10_MODIFIED_URL = "https://nvd.nist.gov/feeds/json/cve/1.0/nvdcve-1.0-modified.json.gz";
    private static final String CVE_JSON_10_BASE_URL = "https://nvd.nist.gov/feeds/json/cve/1.0/nvdcve-1.0-%d.json.gz";
    private static final String CVE_MODIFIED_10_META = "https://nvd.nist.gov/feeds/json/cve/1.0/nvdcve-1.0-modified.meta";
    private static final String CVE_BASE_10_META = "https://nvd.nist.gov/feeds/json/cve/1.0/nvdcve-1.0-%d.meta";

    private static final String CVE_JSON_11_MODIFIED_URL = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-modified.json.gz";
    private static final String CVE_JSON_11_BASE_URL = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-%d.json.gz";
    private static final String CVE_MODIFIED_11_META = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-modified.meta";
    private static final String CVE_BASE_11_META = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-%d.meta";

    private static final Map<String, Map<String, String>> versionToFilenameMaps = new HashMap<>();

    private static final int START_YEAR = 2002;
    private static final int END_YEAR = Calendar.getInstance().get(Calendar.YEAR);

    private File outputDir;
    private boolean downloadFailed = false;
    private final Proxy proxy;

    {
    	Map<String, String> version10Filenames = new HashMap<>();
    	version10Filenames.put("cveJsonModifiedUrl", CVE_JSON_10_MODIFIED_URL);
    	version10Filenames.put("cveJsonBaseUrl", CVE_JSON_10_BASE_URL);
    	version10Filenames.put("cveModifiedMeta", CVE_MODIFIED_10_META);
    	version10Filenames.put("cveBaseMeta", CVE_BASE_10_META);
    	versionToFilenameMaps.put("1.0", version10Filenames);

    	Map<String, String> version11Filenames = new HashMap<>();
    	version11Filenames.put("cveJsonModifiedUrl", CVE_JSON_11_MODIFIED_URL);
    	version11Filenames.put("cveJsonBaseUrl", CVE_JSON_11_BASE_URL);
    	version11Filenames.put("cveModifiedMeta", CVE_MODIFIED_11_META);
    	version11Filenames.put("cveBaseMeta", CVE_BASE_11_META);
    	versionToFilenameMaps.put("1.1", version11Filenames);
    }

    public static void main(String[] args) {
        // Ensure at least one argument was specified
        if (args.length != 1) {
            System.out.println("Usage: java NistDataMirror outputDir");
            return;
        }
        NistDataMirror nvd = new NistDataMirror(args[0]);
        nvd.mirror("1.0");
        nvd.mirror("1.1");
        if (nvd.downloadFailed) {
            System.exit(1);
        }
    }

    public NistDataMirror(String outputDirPath) {
        outputDir = new File(outputDirPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        proxy = initProxy();
    }

    private Proxy initProxy() {
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        if (proxyHost != null && !proxyHost.trim().isEmpty() && proxyPort != null && !proxyPort.trim().isEmpty()) {
            // throws NumberFormatException if proxy port is not numeric
            System.out.println("Using proxy " + proxyHost + ":" + proxyPort);
	    
            String proxyUser = System.getProperty("http.proxyUser");
            String proxyPassword = System.getProperty("http.proxyPassword");
            if (proxyUser != null && !proxyUser.trim().isEmpty() && proxyPassword != null && !proxyPassword.trim().isEmpty()) {

                System.out.println("Using proxy user" + proxyUser + ":" + proxyPassword);

                Authenticator authenticator = new Authenticator() {

                   public PasswordAuthentication getPasswordAuthentication() {
                       return (new PasswordAuthentication(proxyUser,
                             proxyPassword.toCharArray()));
                   }
			
                };
                Authenticator.setDefault(authenticator);
            }

		
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.valueOf(proxyPort)));
        }
        return Proxy.NO_PROXY;
    }

    public void mirror(String version) {
        try {
            Date currentDate = new Date();
            System.out.println("Downloading files at " + currentDate);

            MetaProperties before = readLocalMetaForURL(versionToFilenameMaps.get(version).get("cveModifiedMeta"));
            if (before != null) {
                long seconds = ZonedDateTime.now().toEpochSecond() - before.getLastModifiedDate();
                long hours = seconds / 60 / 60;
                if (hours < 2) {
                    System.out.println("Using local NVD cache as last update was within two hours");
                    return;
                }
            }
            doDownload(versionToFilenameMaps.get(version).get("cveModifiedMeta"));
            MetaProperties after = readLocalMetaForURL(versionToFilenameMaps.get(version).get("cveModifiedMeta"));
            if (before == null || after.getLastModifiedDate() > before.getLastModifiedDate()) {
                doDownload(versionToFilenameMaps.get(version).get("cveJsonModifiedUrl"));
            }
            for (int year = START_YEAR; year <= END_YEAR; year++) {
                downloadVersionForYear(version, year);
            }
        } catch (MirrorException ex) {
            downloadFailed = true;
            System.err.println("Error mirroring the NVD CVE data");
            ex.printStackTrace(System.err);
        }
    }

	private void downloadVersionForYear(String version, int year) throws MirrorException {
		MetaProperties before;
		MetaProperties after;
		String cveBaseMetaUrl = versionToFilenameMaps.get(version).get("cveBaseMeta").replace("%d", String.valueOf(year));
		before = readLocalMetaForURL(cveBaseMetaUrl);
		doDownload(cveBaseMetaUrl);
		after = readLocalMetaForURL(cveBaseMetaUrl);
		if (before == null || after.getLastModifiedDate() > before.getLastModifiedDate()) {
		    String cveJsonBaseUrl = versionToFilenameMaps.get(version).get("cveJsonBaseUrl").replace("%d", String.valueOf(year));
		    doDownload(cveJsonBaseUrl);
		}
	}

    private MetaProperties readLocalMetaForURL(String metaUrl) throws MirrorException {
        URL url;
        try {
            url = new URL(metaUrl);
        } catch (MalformedURLException ex) {
            throw new MirrorException("Invalid url: " + metaUrl, ex);
        }
        MetaProperties meta = null;
        String filename = url.getFile();
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        File file = new File(outputDir, filename).getAbsoluteFile();
        if (file.isFile()) {
            meta = new MetaProperties(file);
        }
        return meta;
    }

    private void doDownload(String nvdUrl) throws MirrorException {
        URL url;
        try {
            url = new URL(nvdUrl);
        } catch (MalformedURLException ex) {
            throw new MirrorException("Invalid url: " + nvdUrl, ex);
        }
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        File file = null;
        boolean success = false;
        try {
            String filename = url.getFile();
            filename = filename.substring(filename.lastIndexOf('/') + 1);
            file = new File(outputDir, filename).getAbsoluteFile();

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
        if (file != null && success) {
            System.out.println("Download succeeded " + file.getName());
            if (file.getName().endsWith(".gz")) {
                uncompress(file);
            }
        }
    }

    private void uncompress(File file) {
        byte[] buffer = new byte[1024];
        GZIPInputStream gzis = null;
        FileOutputStream out = null;
        try {
            File outputFile = new File(file.getAbsolutePath().replaceAll(".gz", ""));
            gzis = new GZIPInputStream(new FileInputStream(file));
            out = new FileOutputStream(outputFile);
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            System.out.println("Uncompressed " + outputFile.getName());
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            close(gzis);
            close(out);
        }
    }

    private void close(Closeable object) {
        if (object != null) {
            try {
                object.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
