# Define the method to remove a key from the map
def remove_key_from_map(dataMap, key_to_remove):
    if key_to_remove in dataMap:
        del dataMap[key_to_remove]
        print("Key '{}' removed from the map.".format(key_to_remove))
    else:
        print("Key '{}' not found in the map.".format(key_to_remove))

# Sample map
towerDatastore = {"test": "value", "key1": "value1", "key2": "value2"}

# Call the method wherever required
remove_key_from_map(towerDatastore, "test")
remove_key_from_map(towerDatastore, "key1")
remove_key_from_map(towerDatastore, "non_existent_key")

# Print updated map
print(towerDatastore)

package org.hz.core.apis
# List of keys to check in releaseVariables
keys_to_check = ["a", "b", "c", "d", "e"]

# Check for falsy values in specified keys
if any(not releaseVariables[key] for key in keys_to_check if key in releaseVariables):
    # Remove the key 'test' from towerDatastore if it exists
    if 'test' in towerDatastore:
        del towerDatastore['test']
        print("Key 'test' removed from towerDatastore")
else:
    print("No falsy values found in specified keys. No action taken.")
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
