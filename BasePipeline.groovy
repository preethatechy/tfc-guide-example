import org.hz.core.common.Checkmarx
import org.hz.core.common.Compliance
import org.hz.core.common.OpenshiftUtil
import org.hz.core.constants.Stages
import org.hz.core.common.SharedPipelineVars
import org.hz.core.common.EnterpriseSecurityUtil
import org.hz.core.constants.PipelineConstants as C
import org.hz.core.apis.MattermostApi
import org.hz.core.apis.CelestialApi
import org.hz.core.common.XlrPluginUtil
import org.hz.core.common.ParamConverterUtil
import org.hz.core.constants.OpenshiftBuildStatergy as OS

abstract class BasePipeline extends CbjWrapper implements Serializable {
    
    BaseValidator validator
    String pipelineType = "CI"

    BasePipeline(cbj, params) {
        super(cbj, params)
    }

    // Main method
    def execute() {
        try {
            timestamps {
                setInitialValues()
                util.validateJobCallingItself()
                onInitialize()
                onAfterInitialize()
                validateInputParams()
                validateAndParameterizeJobs()
                executeStages()
            }
        } catch (Exception err) {
            handleError(err)
        }
    }

    def setInitialValues() {
        initiateWrapper()
        this.validator = new BaseValidator(this)
        
        if (params.help == null) {
            params.help = true
        }

        applySettingsFromParentPipeline()
        globalVars.convertedParams = util.deepCopy(params)
    }

    def onInitialize() {
        debug("Initializing")
    }

    def onAfterInitialize() {
        debug("After Initializing")
    }

    def executeStages() {
        boolean isError = false
        try {
            def agents = validator.createStageList()
            globalVars.pipeline_status = false
            debug("Agents: ${agents}")
            
            Integer agentCnt = 0
            for (entry in agents) {
                debug("Item: ${entry}")
                String agent = entry.agent

                node(agent) {
                    def workspace = util.getWorkSpace()
                    debug("Workspace: ${workspace}")

                    ws(workspace) {
                        try {
                            if (agentCnt == 0) {
                                deleteDir()
                                // Kafka event for cycle start
                                util.getTimeStampForMetrics()
                                pipelineCycleEvent(C.CYCLE_START, C.CYCLE_START_MSG)
                            }

                            if (agentCnt != 0 || globalVars.runAsChildPipeline) {
                                deleteDir()
                                cbj.unstash 'context'
                                globalVars.runAsChildPipeline = false
                            }

                            for (String stageName in entry.stages) {
                                globalVars.stageName = stageName
                                globalVars.agent = agent
                                util.getTimeStampForMetrics()

                                stage(util.getPipelineSpecificStageName()) {
                                print("====")
                                print("Stage Name: ${stageName} running on Label: ${agent} & Node: ${env.NODE_NAME}")
                                print("====")
                                    switch (stageName) {
                                        case Stages.SCM:
                                            ScmDownloadWrapper(stageName)
                                            break
                                        case Stages.ESPSECURITY_SCAN:
                                            espScanStageWrapper(stageName)
                                            break
                                        case Stages.CHECKMARX_SCAN:
                                            checkmarxScanStageWrapper(stageName)
                                            break
                                        case Stages.PREBUILD:
                                            preBuildStageWrapper(stageName)
                                            break
                                        case Stages.BUILD:
                                            buildStageWrapper(stageName)
                                            break
                                        case Stages.UNITTEST:
                                            unitTestStageWrapper(stageName)
                                            break
                                        case Stages.MLIF_SERVICE_GENERATION:
                                            mlifServiceGenerationWrapper(stageName)
                                            break
                                        case Stages.POSTBUILD:
                                            postBuildStageWrapper(stageName)
                                            break
                                        case Stages.PACKAGE:
                                            packageStageWrapper(stageName)
                                            break
                                        case Stages.ARTIFACT:
                                            publishStageWrapper(stageName)
                                            break
                                        case Stages.COMPLIANCE_SCAN:
                                            executeComplianceScanWrapper(stageName)
                                            break
                                        case Stages.TRIGGER_DEPENDENT_JOBS:
                                            triggerDependentJobsWrapper(stageName)
                                            break
                                        case Stages.SCAN:
                                            scanStageWrapper(stageName)
                                            break
                                        case Stages.OPENSHIFT_BUILD:
                                            openShiftBuildStageWrapper(stageName)
                                            break
                                        case Stages.OPENSHIFT_SCAN:
                                            openShiftImageScanStagWrapper(stageName)
                                            break
                                        case Stages.DEPLOY:
                                            deployStageWrapper(stageName)
                                            break
                                        default:
                                            error "Invalid Stage"
                                    }
                                }

                                if (cbj.currentBuild.result != C.UNSTABLE.toUpperCase() || stageName != "Unit Test") {
                                    pipelineMetricsEvent.logEvent(util.getLogMetrics(
                                        stageMessage: "${stageName} successfully completed",
                                        stageResult: "Success"
                                    ))
                                }

                                globalVars.pipeline_status = true
                                globalVars.stageName = "Post Build Activities"
                                agentCnt++
                            if (agentCnt < agents.size()) {
                                if ((!params.publish_mode && params.publish_enableScmTag) || params.scan_enable) {
                                    cbj.stash name: 'context', useDefaultExcludes: false
                                } else {
                                    cbj.stash 'context'
                                }
                            } else if (agentCnt == agents.size()) {
                                if (params.publish_enable) {
                                    debug("Push the build info into Celestial")
                                    def cp = new CelestialApi(this).initialize()
                                     def artifactDetails = util.getArtifactoryDetails()
                                    def publishInfo = cp.defaultPublishInfo()

                            
                                for (artifactdetail in artifactDetails) {
                publishInfo.ArtifactName = artifactdetail.name ?: 'Unknown'
                publishInfo.ArtifactVersion = artifactdetail.version ?: 'Unknown'
                publishInfo.ArtifactPath = artifactdetail.path ?: 'Unknown'
                cp.publishBuildInfo(publishInfo)
            }
                                }
            // Send notifications if enabled
            if (params.notificationDG) 
                                sendEmailNotification()

                if (params.mattermostUrl) 
                    sendMattermostNotification()
                

            applySettingsForChildPipeline()
                            }

        } catch (Exception e) {
            util.throwExp(error: "Error in ${globalVars.stageName} stage.", e)
        } finally {
            // Kafka event for cycle end
            jenkinsfileValidation()
            pipelineCycleEvent(C.CYCLE_END, C.CYCLE_END_MSG, globalVars.stageName)

            if (params.cleanWorkspace) {
                cleanWorkspace()
            }
        }
    }
}
        }
    }
    catch (Exception mex) {
                            pipelineMetricsEvent.logEvent(util.getLogMetrics(
                                stageMessage: "${globalVars.stageName} stage Failed",
                                stageResult: "Failure",
                                errorMessage: "${mex}"
                            ))
                            isError = true
                            util.throwExp(mex)
                        }
                    finally{

            try {
                if (globalVars.warnMsgs) {
                    def msgs = globalVars.warnMsgs.join("<br>")
                    util.displayWarning(msgs)
                }

                if (globalVars.stageCommands && !(params.enablePipelineChaining && !isError)) {
                    StringBuilder sb = new StringBuilder()
                    sb.append("Stage-wise executed commands summary:\n")
                    globalVars.stageCommands.each { stageName, cmds ->
                        sb.append("Stage Name -> ${stageName} \n")

                        if (cmds) {
                            cmds.each { cmd ->
                                cmd.each { script, exitCode ->
                                    sb.append("${script} : ${exitCode}\n")
                                }
                            }
                        }
                    }

                    print(sb.toString())
                }
            } catch (Exception ex) {
                print("Error::: ${ex}")
            }

                    }
        }
    }


    def validateInputParams() {
        debug("Validating Input params")
        this.validator.validateInputParams()
        // debug("Final Params after validation: ${params}")
    }

    /**
     * This method identifies if the Jenkinsfile is an ordinary Jenkinsfile
     * Scenarios considered:
     * 1. Pipeline plugin, no custom logics.
     */
    def jenkinsfileValidation() {
        def paramsutil = new ParamConverterUtil(this)
        paramsutil.replaceJenkinsFileParams()
    }

    def executeUnitTestStage(cmdExecutor) {
        boolean renderReport = false

        try {
            renderReport = cmdExecutor()
        } catch (err) {
            pipelineMetricsEvent.logEvent(util.getLogMetrics("Unit Test(s) Failed ${err}",
                "Failure"
            ))

            if (params.unitTest_failFast) {
                util.throwExp("Stage Unit Test - Exception in executing unit test. Aborting pipeline as unitTest_failFast is true. Exception/Error details: ${err}")
            } else {
                cbj.currentBuild.result = C.UNSTABLE.toUpperCase()
                print("Stage Unit Test - Exception in executing unit test. Not aborting pipeline as unitTest_failFast is false. Exception/Error details: ${err}")
            }
        } finally {
            if (renderReport) {
                print("Stage Unit Test - Rendering test results from path: ${params.unitTest_resultsPath}")
                try {
                    util.renderTestResultToUI(params.unitTest_resultsPath)
                } catch (err) {
                    print("Error while rendering test results: ${err}")
                }
            }
        }
    }


    // Kafka pipeline cycle event handler
    def pipelineCycleEvent(String cycle, String message, String stageName=null) {
        globalVars.stageName = cycle
        pipelineMetricsEvent.logEvent(util.getLogMetrics(cycle, stageResult: "${message}"))

        // Override stage name when stage throws exception
        if (stageName) {
            globalVars.stageName = stageName
        }
    }

    // Wrapper for SCM download
    def ScmDownloadWrapper(stage) {
        onScmDownload(stage)
    }

    // Handle SCM download
    def onScmDownload(stage) {
        debug("SCM: ${scm}")
        cleanPublishArtifactsFolder()

        if (scm != null) {
            debug("SCM: ${scm.getType()}")

            if (scm.getType().toString().contains(C.GIT_SCM_SYSTEM)) {
                params.branchName = scm.getBranches()[0].getName()
                print("Branch Name --${scm.getBranches()[0].getName()}")
            }


        def git = cbj.tool name: 'Default', type: 'git'
        debug("Preparing checkout")
        cbj.checkout scm
    }
    }
    // Handle pre-build stage
    def cleanPublishArtifactsFolder() {}

    // Checkout CioOrg repository based on branch mapping
    def checkoutCioOrgRepositoryBranch() {
        if (!globalVars.isCioOrgCheckoutCompleted) {
            try {
                def org = C.ORG_BRANCH_MAPPING.find { it.org == params.cioorg.toLowerCase() }
                if (org != null) {
                    def repoUrl = configHelper.getConfiguration(key: "scmBaseUrl") + "${org.spk}/${org.repo}.git"
                    cbj.checkout([$class: 'GitSCM', branches: [[name: org.branch]], doGenerateSubmoduleConfigurations: false, extensions: [
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: C.BUILD_SCRIPTS_BASE_FOLDER],
                        [$class: 'LocalBranch', localBranch: org.branch]
                    ], submoduleCfg: [], UserRemoteConfigs: [[credentialsId: C.COMMITTER_JENKINS_CREDS_ID, url: repoUrl]]])

                    if (cbj.isUnix()) {
                        sh : "set +x && chmod -R 755 ${C.BUILD_SCRIPTS_BASE_FOLDER}/*"
                    }
                } else {
                    util.throwExp( "Repository mapping details are missing for Org - ${params.cioorg}. Aborting Build!!")
                }
            } catch (err) {
                util.throwExp(error: "Error while cloning the Cio Org repository for Org - ${params.cioOrg}", err)
            }
            globalVars.isCioOrgCheckoutCompleted = true
        }
    }


def preBuildStageWrapper(stage) {
        checkoutCioOrgRepositoryBranch()
        onPreBuildStage(stage)
    }

    def onPreBuildStage(stage) {
        buildStages(stage)
    }

    def buildStageWrapper(stage) {
        onBuildStage(stage)
    }

    def onBuildStage(stage) {}

    def postBuildStageWrapper(stage) {
        checkoutCioOrgRepositoryBranch()
        onPostBuildStage(stage)
    }

    def onPostBuildStage(stage) {
        buildStages(stage)
    }

    def unitTestStageWrapper(stage) {
        onUnitTestStage(stage)
    }

    def onUnitTestStage(stage) {}

    def mlifServiceGenerationWrapper(stage) {
        onServiceGeneration(stage)
    }

    def onServiceGeneration(stage) {}



def packageStageWrapper(stage) {
        onPackageStage(stage)
    }

    def onPackageStage(stage) {}

    def buildStages(stage) {
        util.validateScriptFiles(stage)
        util.runBuildScripts(stage)
    }

    def publishStageWrapper(stage) {
        onPublishStage(stage)
    }

    def onPublishStage(stage) {}

    def executeComplianceScanWrapper(stage) {
        def compliance = new Compliance(this).initialize()
        compliance.initiateScan()
    }

    def scanStageWrapper(stage) {
        onScanStage(stage)
    }

    def onScanStage(stage) {
        def scanParams = util.getDefaultScanParams()
        util.executeSonarScan(scanParams)
    }

    def openShiftImageScanStagWrapper(stage) {
        openShiftImageScanStage(stage)
    }

    def openShiftBuildStageWrapper(stage) {
        onOpenShiftBuildStage(stage)
    }

    def triggerDependentJobsWrapper(stage) {
        triggerDependentJobs(stage)
    }

    def triggerDependentJobs(stage) {
        util.triggerDependentBuildJobs()
    }

    def validateAndParameterizeJobs() {

    if (params.triggerDepJobs_enable) {
        util.enableBuildJobChaining()
    }
        this.validator.validateBuildJobChain(cbj.params.ancestorJoblist)
    }

    ddef onGetPipelineSpecificOpenshiftParams() {
        def osUtil = new OpenshiftUtil(this).initialize()
        return osUtil.getDefaultCommandInputs()
    }


def sendEmailNotification(isAttachLogs = true) {
    try {
        def buildStatus = cbj.currentBuild.currentResult
        def mailSubject = "[Horizon Jenkins Build ${buildStatus}] ${env.JOB_NAME} ${env.BUILD_NUMBER}"
        def msgBody = C.EMAIL_NOTIFICATION_CI_BODY_TEMPLATE.replace(target: "#USERNAME#", util.getUserName())

        if (buildStatus == C.UNSTABLE) {
            msgBody += C.EMAIL_NOTIFICATION_UNSTABLE_TEMPLATE.replace("#MESSAGE#", globalVars.logEvents["message"])
        msgBody += C.EMAIL_NOTIFICATION_UNSTABLE_TEMPLATE.

        cbj.emailext(
            attachLog: isAttachLogs,
            compressLog: true,
            mimeType: 'text/html',
            body: msgBody,
            subject: mailSubject,
            to: params.notificationDG
        )

        debug("Email notification is successfully sent to ${params.notificationDG}")
    }
    catch(err){
        debug("err")
        }
    }

    def sendMattermostNotification() {
        try {
            debug("MatterMost URL: ${params.mattermostUrl}")
            def buildStatus = cbj.currentBuild.currentResult
            def msgBody = ""

            if (params.addMattermost) {
                msgBody += "${params.addMattermost}"
            }

            msgBody += C.MATTERMOST_NOTIFICATION_BODY
                .replace(target: "#BUILD_ID#", env.BUILD_NUMBER)
                .replace(target: "#BRANCH_NAME#", env.BRANCH_NAME)
                .replace(target: "#BUILD_RESULT#", buildStatus)
                .replace(target: "#BUILD_URL#", env.BUILD_URL)

            if (buildStatus == C.SUCCESS) {
                msgBody = msgBody.replace(target: "#STATUS_SYMBOL#", C.MATTERMOST_GREEN_HEART_EMOJI)
            } else if (buildStatus == C.FAILED || buildStatus == C.UNSTABLE) {
                msgBody = msgBody.replace(target: "#STATUS_SYMBOL#", C.MATTERMOST_BROKEN_HEART_EMOJI)
            }
            def mm =new MattermostApi(this).initialize()
            mm.sendMessage(msgBody)
        } catch (err) {
            debug("Error sending Mattermost notification: ${err}")
        }
    }


def openShiftImageScanStage(stage) {
        def renderReport = false
        try {
            def osUtil = new OpenshiftUtil(this).initialize()
            def inp = onGetPipelineSpecificOpenshiftParams()
            inp.openshiftImageTag = (params.oscBuild_statergy == OS.VENDOR_IMAGE) ? params.osc_ImageTag : inp.openshiftImageTag
            osUtil.runAquaScan(inp)
        } catch (err) {
            renderReport = err.getMessage().contains("Status Code : 4")

            if (params.oscScan_failFast) {
                util.throwExp(error: "Aborting pipeline as oscScan_failFast is true. Exception/Error detail: ${err}", err)
            } else {
                warn("Aqua scan failed. Not aborting pipeline as oscScan_failFast is false. Exception/Error detail: ${err}")
            }
        } finally {
            if (renderReport) {
                print("Publishing Aqua Scan results file: aqua-scan.html")
                try {
                    cbj.publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: false,
                        keepAll: true,
                        reportDir: './',
                        includes: 'aqua-scan.html',
                        reportFile: 'aqua-scan.html'
                    ])
                } catch (err) {
                    warn("Exception in publishing aqua scan results. Exception/Error detail: ${err}")
                }
            }
        }
    }

def onOpenShiftBuildStage(stage) {
        def osUtil = new OpenshiftUtil(this).initialize()
        osUtil.cleanFilesAndFolders()

        def inp = onGetPipelineSpecificOpenshiftParams()

        osUtil.login(inp)
        osUtil.buildImage(inp)
        osUtil.logout()
        osUtil.publishBuildInfo(inp)
    }

    def getServerInfo() {
        String artifactoryUrl = configHelper.getConfiguration(key: 'artifactoryUrl')
        String artifactoryTimeout = configHelper.getConfiguration(key: 'artifactoryTimeout')
        String credentialId = configHelper.getConfiguration(C.ARTIFACTORY_WRITE_USER_ID)

        debug("branch name: ${env.BRANCH_NAME}")
        debug("artifactoryUrl: ${artifactoryUrl}")

        def server = cbj.Artifactory.newServer(url: artifactoryUrl, credentialsId: credentialId)
        server.setBypassProxy(true)
        server.getConnection().setTimeout(artifactoryTimeout?.toInteger())

        return server
    }

    def espScanStageWrapper(stage) {
        onEspScanStage(stage)
    }

    def onEspScanStage(stage) {
        def espUtil = new EnterpriseSecurityUtil(this)
        espUtil.onEspScan()
    }


def checkmarxScanStageWrapper(stageName) {
        def checkmark = new Checkmarx(this).initialize()
        checkmark.checkmarxScan(stageName)
    }

    def executePublishStage(publishMethod, extraProps = null) {
        debug("Publishing artifacts using build definition.")
        try {
            if (params.deploy_enable) {
def xlrUtil = new XlrPluginUtil(this).initialize()
            xlrUtil.checkXlrReleaseLockStatus()
                        }

            

            def server = getServerInfo()
            def buildInfo = util.getBuildInfo()

            debug("Calling publishMethod")
            publishMethod(server, buildInfo, extraProps)
            debug("publishMethod called successfully")

            debug("buildInfo: ${buildInfo}")
            server.publishBuildInfo(buildInfo)

            // Create and push tag to remote (commented out as per the original code)
            createAndPushGitTagToRemote()

            if (params.publish_dmz) {
                debug("publish_dmz flag is true")
                util.publishDMZ(util.getPublishRepo())
            }
        } catch (err) {
            util.throwExp(error: "Publish Artifact failed", err)
        }
    }

def createAndPushGitTagToRemote() {
        def gitUrl = scm.getUserRemoteConfigs()[0].getUrl()
        def jenkinsCred = util.getCredential(C.COMMITER_JENKINS_CREDS_ID)

        // Replace git URL with credentials
        gitUrl = gitUrl.replace("https://", "https://${jenkinsCred.userName}:${jenkinsCred.password}@")

        // Push git local tags to Remote repo
        if (params.publish_enableLocalGitTagToRemote) {
        def git = cbj.tool name: 'Default', type: 'git'

        def gitTagCommands = "$git config --local user.name \"${C.COMMITER_USER_NAME}\" && $git config --local user.email \"${C.COMMITER_EMAIL_ID}\" && $git push ${gitUrl} --tags"
        util.executeCommand(gitTagCommands)
                }



        if (params.publish_enableScmTag && env.BRANCH_NAME.startsWith(C.RELEASE_BRANCH_PREFIX)) {
            def gitTag = scm.getBranches()[0].getName() + " ${env.BUILD_NUMBER}"
            def gitTagMsg = "Automatically tagged for Release"
            def git = cbj.tool name: 'Default', type: 'git'

            git = "\"$git\""

            def gitTagCommands = "$git config --local user.name \"${C.COMMITER_USER_NAME}\" && $git config --local user.email \"${C.COMMITER_EMAIL_ID}\" && $git tag -a ${gitTag} -m \"${gitTagMsg}\" && $git push ${gitUrl} --tags"

            try {
                if (params.customCheckoutDir != null) {
                    cbj.dir("${params.customCheckoutDir}") {
                        util.executeCommand(gitTagCommands)
                    }
                } else {
                    util.executeCommand(gitTagCommands)
                }
            } catch (e) {
                warn("Failed to create tag due to error: ${e}")
            }
        }
    }


def applySettingsFromParentPipeline() {
        try {
            if (params.enablePipelineChaining || SharedPipelineVars.parentPipelineParams?.enablePipelineChaining) {
                SharedPipelineVars.pipelineCount++
            }
                if (SharedPipelineVars.parentPipelineParams?.enablePipelineChaining) {
                    globalVars.runAsChildPipeline = true
                    globalVars.isChildPipeline = true
                    globalVars.stageCommands = SharedPipelineVars.stageCommands
                    SharedPipelineVars.stageCommands = null

                    if (params.inheritParentPipelineParams) {
debug("parentParams ${SharedPipelineVars.parentPipelineParams}")
                    debug("childParams ${params}")

                    SharedPipelineVars.parentPipelineParams.enablePipelineChaining = false
                    params = SharedPipelineVars.parentPipelineParams + params
                    debug("Merged Params ${params}")
                                        }

                    

                    SharedPipelineVars.parentPipelineParams = null
                }
            }
        catch (err) {
            util.throwExp("Unable to stash:" + err, err)
        }
    }

    def applySettingsForChildPipeline() {
        try {
            if (params.enablePipelineChaining) {
                debug("Saving parents params for child")

                SharedPipelineVars.parentPipelineParams = params
                SharedPipelineVars.stageCommands = globalVars.stageCommands
                cbj.stash name: 'context', useDefaultExcludes: false
            }
        } catch (err) {
            util.throwExp(error: "Unable to stash: ${err}", err)
        }
    }

    def deployStageWrapper(stage) {
        onDeployStage(stage)
    }

    def onDeployStage(stage) {
        def xlrUtil: XlrPluginUtil = new XlrPluginUtil(this).initialize()
        xlrUtil.triggerXlrReleaseProcess()
        return
    }
}
