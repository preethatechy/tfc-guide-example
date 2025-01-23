package org.hz.core.apis

import groovyx.net.http.ContentType
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
import org.hz.core.base.BaseApi
import org.hz.core.base.CbjWrapper
import java.nio.charset.StandardCharsets

class ArtifactoryApi extends BaseApi {

    ArtifactoryApi(CbjWrapper obj) {
        super(obj)
    }

    def initialize() {
        setBaseUrl(configHelper.getConfiguration(key: 'artifactoryUrl'))
        headers["Authorization"] = "Basic " + util.getArtifactoryCredentialAsBase64()
        headers['Content-Type'] = 'text/plain'
        return this
    }

    def searchProperties(String repo, String buildName, String buildNo, Map extraParams = [:]) {
        String path = "/api/search/prop"
        Map query = [
            "repos"       : repo,
            "build.name"  : buildName,
            "build.number": buildNo
        ]
        query = query + extraParams
        query = query.findAll { it.value }
        def resp = get(path, [query: query])
        return resp.getData()
    }

    def searchArtifactsByAQL() {
        String path = "/api/search/aql"
        def payLoad = 'items.find({"@build.name":"' + util.getBuildInfo().getName() + '"},{"@build.number":"' + util.getBuildInfo().getNumber() + '"})'
        def resp = post(path, [body: payLoad])
        return resp?.getData()?.results
    }

    def searchValue(url) {
        String path = url
        debug("path: ${path}")
        def resp = get(path, [headers: ["Accept": "application/json"]])
        def res = resp
        debug("res: ${res}")
        return res.getData()
    }

    def tagArtifacts(artifactFullUrl, tag, dateTimeNowUTC) {
        def prop = URLEncoder.encode(tag, StandardCharsets.UTF_8.toString()) + dateTimeNowUTC
        def path = artifactFullUrl
        Map query = [
            "properties": prop
        ]
        def response = put(path, [query: query])
        return response.status
    }

    def tag(artifactUriList, tagName, tagValue) {
        if (artifactUriList) {
            for (artifactUri in artifactUriList) {
                String tagUrl = artifactUri
                debug("Tag Url: ${tagUrl}")
                String props = "${tagName}=${tagValue}"
                put(tagUrl.replace(basePath, ""), [query: [properties: props]])
            }
        } else {
            print("artifactUriList is null or empty, nothing to tag.")
        }
    }

    def getArtifactUrl(payLoad) {
        String path = '/api/search/aql'
        def response = post(path, [body: payLoad, contentType: ContentType.TEXT])
        return response?.getData()?.results
    }
}
