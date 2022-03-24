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


import java.io.*;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * This self-contained class can be called from the command-line. It downloads
 * the contents of NVD CPE/CVE JSON data to the specified output path.
 *
 * @author Steve Springett (steve.springett@owasp.org)
 */
public class NistDataMirror {

    /**
     * Exit code used when download failed.
     */
    private static final int EXIT_CODE_DOWNLOAD_FAILED = 1;
    /**
     * Exit code used when tool was invoked with wrong arguments.
     */
    private static final int EXIT_CODE_WRONG_INVOCATION = 2;

    private static final String CVE_JSON_11_MODIFIED_URL = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-modified.json.gz";
    private static final String CVE_JSON_11_RECENT_URL = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-recent.json.gz";
    private static final String CVE_JSON_11_BASE_URL = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-%d.json.gz";
    private static final String CVE_MODIFIED_11_META = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-modified.meta";
    private static final String CVE_RECENT_11_META = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-recent.meta";
    private static final String CVE_BASE_11_META = "https://nvd.nist.gov/feeds/json/cve/1.1/nvdcve-1.1-%d.meta";
    private static final Map<String, Map<String, String>> versionToFilenameMaps = new HashMap<>();
    private static final int START_YEAR = 2002;
    private static final int END_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    private File outputDir;
    private boolean downloadFailed = false;
    private final Proxy proxy;

    {
        Map<String, String> version11Filenames = new HashMap<>();
        version11Filenames.put("cveJsonModifiedUrl", CVE_JSON_11_MODIFIED_URL);
        version11Filenames.put("cveJsonRecentUrl", CVE_JSON_11_RECENT_URL);
        version11Filenames.put("cveJsonBaseUrl", CVE_JSON_11_BASE_URL);
        version11Filenames.put("cveModifiedMeta", CVE_MODIFIED_11_META);
        version11Filenames.put("cveRecentMeta", CVE_RECENT_11_META);
        version11Filenames.put("cveBaseMeta", CVE_BASE_11_META);
        versionToFilenameMaps.put("1.1", version11Filenames);
    }

    public static void main(String[] args) {
        // Ensure at least one argument was specified
        if (args.length != 1) {
            System.out.println("Usage: java NistDataMirror outputDir");
            System.exit(EXIT_CODE_WRONG_INVOCATION);
            return;
        }
        NistDataMirror nvd = new NistDataMirror(args[0]);
        nvd.mirror("1.1");
        if (nvd.downloadFailed) {
            System.exit(EXIT_CODE_DOWNLOAD_FAILED);
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
                System.out.println("Using proxy user " + proxyUser + ":" + proxyPassword);
                Authenticator authenticator = new Authenticator() {

                    @Override
                    public PasswordAuthentication getPasswordAuthentication() {
                        return (new PasswordAuthentication(proxyUser,
                                proxyPassword.toCharArray()));
                    }

                };
                Authenticator.setDefault(authenticator);
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
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
            before = readLocalMetaForURL(versionToFilenameMaps.get(version).get("cveRecentMeta"));
            doDownload(versionToFilenameMaps.get(version).get("cveRecentMeta"));
            after = readLocalMetaForURL(versionToFilenameMaps.get(version).get("cveRecentMeta"));
            if (before == null || after.getLastModifiedDate() > before.getLastModifiedDate()) {
                doDownload(versionToFilenameMaps.get(version).get("cveJsonRecentUrl"));
            }
            for (int year = START_YEAR; year <= END_YEAR; year++) {
                downloadVersionForYear(version, year);
                Boolean valid = validCheck(year);
                System.out.println("File " + year + " is valid.");
                if (Boolean.FALSE.equals(valid)) {
                    int i = 0;
                    while (i < 2) {
                        downloadVersionForYear(version, year);
                        Boolean valid2 = validCheck(year);
                        i++;
                        if (Boolean.TRUE.equals(valid2)) {
                            System.out.println("File " + year + " is valid.");
                            break;
                        }
                    }
                    System.out.println("The File " + year + " is corrupted");
                }
            }

        } catch (MirrorException ex) {
            downloadFailed = true;
            System.err.println("Error mirroring the NVD CVE data");
            ex.printStackTrace(System.err);
        } catch (Exception e) {
            e.printStackTrace();
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
            long count = 0;
            while ((i = bis.read()) != -1) {
                bos.write(i);
                count++;
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
        InputStream gzis = null;
        OutputStream out = null;
        try {
            File outputFile = new File(file.getAbsolutePath().replaceAll(".gz", ""));
            gzis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(file)));
            out = new BufferedOutputStream(new FileOutputStream(outputFile));
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

    /**
     * This function checks, if the generated Hash of the json file matches the
     * hashcode in the meta file
     *
     * @param year
     * @return true or false
     */
    private Boolean validCheck(int year) {
        try {
            Path metaFilePath = Paths.get(String.valueOf(outputDir), "nvdcve-1.1-" + year + ".meta");
            int n = 4; // The line number where the hash is saved in the meta file
            String hashLine = Files.readAllLines(Paths.get(String.valueOf(metaFilePath))).get(n);
            String metaHash = hashLine.substring(7);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            Path jsonFilePath = Paths.get(String.valueOf(outputDir), "nvdcve-1.1-" + year + ".json");
            String hex = checksum(String.valueOf(jsonFilePath), md);

            return metaHash.equals(hex);
        } catch (IOException | NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static String checksum(String filepath, MessageDigest md) throws IOException {

        // file hashing with DigestInputStream
        try ( DigestInputStream dis = new DigestInputStream(new BufferedInputStream(new FileInputStream(filepath)), md)) {
            while (dis.read() != -1) ; //empty loop to clear the data
            md = dis.getMessageDigest();
        }

        // bytes to hex
        final byte[] digest = md.digest();
        String digestHexString = bytesToHex(digest);
        return digestHexString.toUpperCase(Locale.ROOT);

    }

    // https://stackoverflow.com/a/9855338/53897
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
