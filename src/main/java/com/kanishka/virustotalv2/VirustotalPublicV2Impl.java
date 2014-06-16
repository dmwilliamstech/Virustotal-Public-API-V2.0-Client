/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kanishka.virustotalv2;

import com.google.gson.Gson;
import com.kanishka.net.commons.BasicHTTPRequestImpl;
import com.kanishka.net.commons.HTTPRequest;
import com.kanishka.net.model.HttpStatus;
import com.kanishka.net.model.MultiPartEntity;
import com.kanishka.net.model.RequestMethod;
import com.kanishka.net.model.Response;
import com.kanishka.virustotal.dto.DomainReport;
import com.kanishka.virustotal.dto.FileScanReport;
import com.kanishka.virustotal.dto.GeneralResponse;
import com.kanishka.virustotal.dto.IPAddressReport;
import com.kanishka.virustotal.dto.ScanInfo;
import com.kanishka.virustotal.exception.APIKeyNotFoundException;
import com.kanishka.virustotal.exception.InvalidArguentsException;
import com.kanishka.virustotal.exception.QuotaExceededException;
import com.kanishka.virustotal.exception.UnauthorizedAccessException;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kdkanishka@gmail.com
 */
public class VirustotalPublicV2Impl implements VirustotalPublicV2 {

    private Gson gsonProcessor;
    private String apiKey;
    private static final String API_KEY_FIELD = "apikey";
    private static final String RESOURCE_FIELD = "resource";
    private static final String ERR_MSG_EXCEED_MAX_REQ_PM = "Exceeded maximum number of requests per minute, Please try again later.";
    private static final String ERR_MSG_INVALID_API_KEY = "Invalid api key";
    private HTTPRequest httpRequestObject;

    public VirustotalPublicV2Impl() throws APIKeyNotFoundException {
        initialize();
        httpRequestObject = new BasicHTTPRequestImpl();
    }

    public VirustotalPublicV2Impl(HTTPRequest httpRequestObject) throws APIKeyNotFoundException {
        initialize();
        this.httpRequestObject = httpRequestObject;
    }

    private void initialize() throws APIKeyNotFoundException {
        gsonProcessor = new Gson();
        apiKey = VirusTotalConfig.getConfigInstance().getVirusTotalAPIKey();
        if (apiKey == null || apiKey.length() == 0) {
            throw new APIKeyNotFoundException("API Key is not set. Please set api key.\nSample : VirusTotalConfig.getConfigInstance().setVirusTotalAPIKey(\"APIKEY\")");
        }
    }

    @Override
    public ScanInfo scanFile(File fileToScan) throws UnsupportedEncodingException, UnauthorizedAccessException, FileNotFoundException, QuotaExceededException {
        if (!fileToScan.canRead()) {
            throw new FileNotFoundException("Could not access file, either the file may not exists or not accessible!");
        }
        Response responseWrapper = new Response();
        ScanInfo scanInfo = new ScanInfo();

        FileBody fileBody = new FileBody(fileToScan);
        MultiPartEntity file = new MultiPartEntity("file", fileBody);
        MultiPartEntity apikey = new MultiPartEntity(API_KEY_FIELD, new StringBody(apiKey));
        List<MultiPartEntity> multiParts = new ArrayList<MultiPartEntity>();
        multiParts.add(file);
        multiParts.add(apikey);
        Integer statusCode = -1;
        HttpStatus httpStatus = new HttpStatus();
        try {
            responseWrapper = httpRequestObject.request(URI_VT2_FILE_SCAN, null, null, RequestMethod.GET, multiParts, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }
        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            scanInfo = gsonProcessor.fromJson(serviceResponse, ScanInfo.class);
        }
        return scanInfo;

    }

    @Override
    public ScanInfo[] reScanFiles(String[] resources) throws UnsupportedEncodingException, UnauthorizedAccessException, InvalidArguentsException, QuotaExceededException {
        ScanInfo[] scanInfo = null;
        if (resources == null) {
            throw new InvalidArguentsException("Incorrect parameter \'resources\', resource should be an array with at least one element");
        }
        Response responseWrapper = new Response();

        MultiPartEntity apikey = new MultiPartEntity(API_KEY_FIELD, new StringBody(apiKey));
        StringBuilder resourceStr = new StringBuilder();
        for (String resource : resources) {
            resourceStr.append(resource).append(", ");
        }
        //clean up resource string
        int lastCommaIdx = resourceStr.lastIndexOf(",");
        if (lastCommaIdx > 0) {
            resourceStr.deleteCharAt(lastCommaIdx);
        }

        MultiPartEntity part = new MultiPartEntity(RESOURCE_FIELD, new StringBody(resourceStr.toString()));
        List<MultiPartEntity> multiParts = new ArrayList<MultiPartEntity>();
        multiParts.add(part);
        multiParts.add(apikey);
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(URI_VT2_RESCAN, null, null, RequestMethod.POST, multiParts, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            if (resources.length > 1) {
                scanInfo = gsonProcessor.fromJson(serviceResponse, ScanInfo[].class);
            } else {
                ScanInfo scanInfo1Elem = gsonProcessor.fromJson(serviceResponse, ScanInfo.class);
                scanInfo = new ScanInfo[]{scanInfo1Elem};
            }
        }

        return scanInfo;
    }

    @Override
    public FileScanReport getScanReport(String resource, String format = "fileScanReport") throws UnsupportedEncodingException, UnauthorizedAccessException, QuotaExceededException {
        Response responseWrapper = new Response();
        FileScanReport fileScanReport = new FileScanReport();

        MultiPartEntity apikey = new MultiPartEntity(API_KEY_FIELD, new StringBody(apiKey));
        MultiPartEntity resourcePart = new MultiPartEntity(RESOURCE_FIELD, new StringBody(resource));
        List<MultiPartEntity> multiParts = new ArrayList<MultiPartEntity>();
        multiParts.add(apikey);
        multiParts.add(resourcePart);
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(URI_VT2_FILE_SCAN_REPORT, null, null, RequestMethod.POST, multiParts, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            switch (format.toLowerCase()) {
                case "json":
                    fileScanReport = serviceResponse;
                    break;
                case "fileScanReport":
                    fileScanReport = gsonProcessor.fromJson(serviceResponse, FileScanReport.class);
                    break;
                default:
                    System.out.println("Invalid format")
            }
        }
        return fileScanReport;
    }

    @Override
    public FileScanReport[] getScanReports(String[] resources) throws UnsupportedEncodingException, UnauthorizedAccessException, QuotaExceededException, InvalidArguentsException {
        Response responseWrapper = new Response();
        FileScanReport[] fileScanReport = null;
        if (resources == null) {
            throw new InvalidArguentsException("Incorrect parameter \'resources\', resource should be an array with at least one element");
        }

        MultiPartEntity apikey = new MultiPartEntity(API_KEY_FIELD, new StringBody(apiKey));
        StringBuilder resourceStr = new StringBuilder();
        for (String resource : resources) {
            resourceStr.append(resource).append(", ");
        }
        //clean up resource string
        int lastCommaIdx = resourceStr.lastIndexOf(",");
        if (lastCommaIdx > 0) {
            resourceStr.deleteCharAt(lastCommaIdx);
        }

        MultiPartEntity part = new MultiPartEntity(RESOURCE_FIELD, new StringBody(resourceStr.toString()));
        List<MultiPartEntity> multiParts = new ArrayList<MultiPartEntity>();
        multiParts.add(apikey);
        multiParts.add(part);
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(URI_VT2_FILE_SCAN_REPORT, null, null, RequestMethod.POST, multiParts, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            if (resources.length > 1) {
                fileScanReport = gsonProcessor.fromJson(serviceResponse, FileScanReport[].class);
            } else {
                FileScanReport fScanRep = gsonProcessor.fromJson(serviceResponse, FileScanReport.class);
                fileScanReport = new FileScanReport[]{fScanRep};
            }
        }
        return fileScanReport;
    }

    @Override
    public ScanInfo[] scanUrls(String[] urls) throws UnsupportedEncodingException, UnauthorizedAccessException, QuotaExceededException, InvalidArguentsException {
        Response responseWrapper = new Response();
        ScanInfo[] scanInfo = null;
        if (urls == null) {
            throw new InvalidArguentsException("Incorrect parameter \'urls\' , urls should be an array with at least one element ");
        } else if (urls.length > VT2_MAX_ALLOWED_URLS_PER_REQUEST) {
            throw new InvalidArguentsException("Incorrect parameter \'urls\' , maximum number(" + VT2_MAX_ALLOWED_URLS_PER_REQUEST + ") of urls per request has been exceeded.");
        }
        MultiPartEntity apikey = new MultiPartEntity(API_KEY_FIELD, new StringBody(apiKey));
        StringBuilder resourceStr = new StringBuilder();
        for (String url : urls) {
            resourceStr.append(url).append(VT2_URLSEPERATOR);
        }
        //clean up resource string
        int lastUrlSepIdx = resourceStr.lastIndexOf(VT2_URLSEPERATOR);
        if (lastUrlSepIdx > 0) {
            resourceStr.deleteCharAt(lastUrlSepIdx);
        }

        MultiPartEntity part = new MultiPartEntity("url", new StringBody(resourceStr.toString()));
        List<MultiPartEntity> multiParts = new ArrayList<MultiPartEntity>();
        multiParts.add(apikey);
        multiParts.add(part);
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(URI_VT2_URL_SCAN, null, null, RequestMethod.POST, multiParts, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            if (urls.length > 1) {
                scanInfo = gsonProcessor.fromJson(serviceResponse, ScanInfo[].class);
            } else {
                ScanInfo scanInforElem = gsonProcessor.fromJson(serviceResponse, ScanInfo.class);
                scanInfo = new ScanInfo[]{scanInforElem};
            }
        }
        return scanInfo;
    }

    @Override
    public FileScanReport[] getUrlScanReport(String[] urls, boolean scan) throws UnsupportedEncodingException, UnauthorizedAccessException, QuotaExceededException, InvalidArguentsException {
        Response responseWrapper = new Response();
        FileScanReport[] fileScanReport = null;
        if (urls == null) {
            throw new InvalidArguentsException("Incorrect parameter \'resources\', resource should be an array with at least one element");
        } else if (urls.length > VT2_MAX_ALLOWED_URLS_PER_REQUEST) {
            throw new InvalidArguentsException("Incorrect parameter \'urls\' , maximum number(" + VT2_MAX_ALLOWED_URLS_PER_REQUEST + ") of urls per request has been exceeded.");
        }

        MultiPartEntity apikey = new MultiPartEntity(API_KEY_FIELD, new StringBody(apiKey));
        StringBuilder resourceStr = new StringBuilder();
        for (String resource : urls) {
            resourceStr.append(resource).append(", ");
        }
        //clean up resource string
        int lastCommaIdx = resourceStr.lastIndexOf(",");
        if (lastCommaIdx > 0) {
            resourceStr.deleteCharAt(lastCommaIdx);
        }

        MultiPartEntity part = new MultiPartEntity(RESOURCE_FIELD, new StringBody(resourceStr.toString()));
        List<MultiPartEntity> multiParts = new ArrayList<MultiPartEntity>();
        multiParts.add(apikey);
        multiParts.add(part);

        if (scan) {
            MultiPartEntity scanPart = new MultiPartEntity("scan", new StringBody("1"));
            multiParts.add(scanPart);
        }
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(URI_VT2_URL_SCAN_REPORT, null, null, RequestMethod.POST, multiParts, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            if (urls.length > 1) {
                fileScanReport = gsonProcessor.fromJson(serviceResponse, FileScanReport[].class);
            } else {
                FileScanReport fileScanReportElem = gsonProcessor.fromJson(serviceResponse, FileScanReport.class);
                fileScanReport = new FileScanReport[]{fileScanReportElem};
            }
        }
        return fileScanReport;
    }

    @Override
    public IPAddressReport getIPAddresReport(String ipAddress) throws InvalidArguentsException, QuotaExceededException, UnauthorizedAccessException {
        Response responseWrapper = new Response();
        IPAddressReport ipReport = new IPAddressReport();
        if (ipAddress == null) {
            throw new InvalidArguentsException("Incorrect parameter \'ipAddress\', it should be a valid IP address ");
        }

        String uriWithParams = URI_VT2_IP_REPORT + "?apikey=" + apiKey + "&ip=" + ipAddress;
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(uriWithParams, null, null, RequestMethod.GET, null, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            ipReport = gsonProcessor.fromJson(serviceResponse, IPAddressReport.class);
        }
        return ipReport;
    }

    @Override
    public DomainReport getDomainReport(String domain) throws InvalidArguentsException, UnauthorizedAccessException, QuotaExceededException {
        Response responseWrapper = new Response();
        DomainReport domainReport = new DomainReport();
        if (domain == null) {
            throw new InvalidArguentsException("Incorrect parameter \'domain\', it should be a valid domain name");
        }

        String uriWithParams = URI_VT2_DOMAIN_REPORT + "?apikey=" + apiKey + "&domain=" + domain;
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(uriWithParams, null, null, RequestMethod.GET, null, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            domainReport = gsonProcessor.fromJson(serviceResponse, DomainReport.class);
        }
        return domainReport;
    }

    @Override
    public GeneralResponse makeAComment(String resource, String comment) throws UnsupportedEncodingException, UnauthorizedAccessException, InvalidArguentsException, QuotaExceededException {
        if (resource == null || resource.length() == 0) {
            throw new InvalidArguentsException("Incorrect parameter \'resource\', it should be a string representing a hash value (md2,sha1,sha256)");
        }
        Response responseWrapper = new Response();

        GeneralResponse generalResponse = new GeneralResponse();
        generalResponse.setResponseCode(-1);
        generalResponse.setVerboseMessage("Could not publish the comment, API error occured!");

        MultiPartEntity apikey = new MultiPartEntity(API_KEY_FIELD, new StringBody(apiKey));
        MultiPartEntity resourcePart = new MultiPartEntity(RESOURCE_FIELD, new StringBody(resource));
        MultiPartEntity commentPart = new MultiPartEntity("comment", new StringBody(comment));
        List<MultiPartEntity> multiParts = new ArrayList<MultiPartEntity>();
        multiParts.add(apikey);
        multiParts.add(resourcePart);
        multiParts.add(commentPart);
        HttpStatus httpStatus = new HttpStatus();
        Integer statusCode = -1;

        try {
            responseWrapper = httpRequestObject.request(URI_VT2_PUT_COMMENT, null, null, RequestMethod.POST, null, httpStatus);
            statusCode = httpStatus.getStatusCode();
        } catch (IOException e) {
            statusCode = httpStatus.getStatusCode();
            if (statusCode == VirustotalStatus.FORBIDDEN) {
                //fobidden
                throw new UnauthorizedAccessException(ERR_MSG_INVALID_API_KEY, e);
            } else if (statusCode == VirustotalStatus.API_LIMIT_EXCEEDED) {
                //limit exceeded
                throw new QuotaExceededException(ERR_MSG_EXCEED_MAX_REQ_PM, e);
            }
        }

        if (statusCode == VirustotalStatus.SUCCESSFUL) {
            //valid response
            String serviceResponse = responseWrapper.getResponse();
            generalResponse = gsonProcessor.fromJson(serviceResponse, GeneralResponse.class);
        }
        return generalResponse;
    }
}
