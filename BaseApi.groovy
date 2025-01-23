package org.hz.core.base
import groovyx.net.http.RESTClient
import groovyx.net.http.AsyncHTTPBuilder

class BaseApi extends CbjWrapper implements Serializable {

    def basePath
    def headers = ["accept": "application/json", "Content-Type": "application/json"]

    BaseApi(CbjWrapper obj) {
        super(obj)
    }

    def setBaseUrl(String url) {
        basePath = url.endsWith("/") ? url : url + "/"
    }

    def getRestClientRequest(String path, Map request = [:]) {
        debug("request: ${request}")
        def reqObj = [
            headers: request.headers ? combineHeaders(request.headers) : headers,
            path: path?.startsWith("/") ? path.substring(1) : path,
            query: request.query,
            requestContentType: request.contentType ?: headers["Content-Type"],
            body: request.body
        ]
        debug("basePath: ${basePath}, reqObj: ${reqObj}")
        return reqObj
    }

    def get(String path, Map<String, Object> request = [:]) {
        def restClient = new RESTClient(basePath)
        return restClient.get(getRestClientRequest(path, request))
    }

    def post(String path, Map<String, Object> request = [:]) {
        def restClient = new RESTClient(basePath)
        def response = restClient.post(getRestClientRequest(path, request))
        debug("Response Status: ${response?.status}")
        return response
    }

    def postAsync(String path, Map<String, Object> request = [:]) {
        def asyncHttpBuilder = new AsyncHTTPBuilder(uri: basePath, contentType: headers["Content-Type"])
        return asyncHttpBuilder.post(getRestClientRequest(path, request))
    }

    def put(String path, Map<String, Object> request = [:]) {
        def restClient = new RESTClient(basePath)
        return restClient.put(getRestClientRequest(path, request))
    }

    def delete(String path, Map<String, Object> request = [:]) {
        def restClient = new RESTClient(basePath)
        return restClient.delete(getRestClientRequest(path, request))
    }

    def httpRequest(Map<String, Object> request = [:]) {
        return getHttpRequest(request)
    }

    def getHttpRequest(Map request = [:]) {
        def respObj = cbj.httpRequest(
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            customHeaders: request.headers,
            httpMode: "POST",
            requestBody: request.body,
            url: basePath,
            consoleLogResponseBody: false,
            quiet: true,
            validResponseCodes: request.codes,
            timeout: request.timeout
        )
        return respObj
    }

    def handleResponse(path, response) {
        if (response.status >= 200 && response.status < 300) {
            String ct = response.headers['Content-Type']
            if (ct?.toLowerCase()?.contains('application/json')) {
                return util.parseJsonText(response.getData())
            }
            return response
        } else {
            util.throwExp(error: "Rest api failed for url:${basePath} Path: ${path}, Status: ${response?.status} ${response}")
        }
    }

    def combineHeaders(Map methodHeaders) {
        def allHeaders = [:]
        allHeaders.putAll(headers)
        allHeaders.putAll(methodHeaders)
        return allHeaders
    }
}
