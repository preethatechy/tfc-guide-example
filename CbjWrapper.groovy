abstract class CbjWrapper implements Serializable {

    // Instance variables
    public cbj
    public params
    public env
    public scm
    public globalVars = [:]
    public Util util
    public ConfigurationHelper configHelper
    public PipelineMetricsEvent pipelineMetricsEvent

    // Constructors
    CbjWrapper(cbj, params) {
        this.cbj = cbj
        this.params = params
        this.env = this.cbj.env
        this.scm = this.cbj.scm
    }

    CbjWrapper(cbj, params, globalVars) {
        this(cbj, params)
        this.globalVars = globalVars
    }

    CbjWrapper(CbjWrapper obj) {
        this.cbj = obj.cbj
        this.params = obj.params
        this.env = obj.env
        this.scm = obj.scm
        this.globalVars = obj.globalVars
        this.util = obj.util
        this.configHelper = obj.configHelper
        this.pipelineMetricsEvent = obj.pipelineMetricsEvent
    }

    // Method to initialize wrapper
    def initiateWrapper() {
        if (!this.util) {
            this.util = new Util(cbj, params, globalVars)
        }
        if (!this.configHelper) {
            this.configHelper = new ConfigurationHelper(cbj, params, globalVars)
            this.configHelper.initializeConfig()
        }
        this.util.configHelper = this.configHelper
        if (!this.pipelineMetricsEvent) {
            this.pipelineMetricsEvent = new PipelineMetricsEvent(this).initialize()
        }
    }

    // Method to execute commands with parameters
    def cmd(script, Map cmdParams = [:]) {
        if (script instanceof Map) {
            cmdParams = script
            script = cmdParams.script ?: ''
        }

        def returnStatus = cmdParams.returnStatus ?: false
        def returnStdout = cmdParams.returnStdout ?: false
        def label = cmdParams.label ?: ''
        def encoding = cmdParams.encoding ?: 'UTF-8'
        def cmdExecutionStatus = null

        if (isUnix()) {
            if (!cmdParams.echo) {
                script = "set +x && ${script}"
            }
            debug("Executing command: ${script}")
            cmdExecutionStatus = cbj.sh(script: script, returnStatus: returnStatus, returnStdout: returnStdout)
        } else {
            debug("Executing command: ${script}")
            cmdExecutionStatus = cbj.bat(script: script, returnStatus: returnStatus, returnStdout: returnStdout)
        }

        if (cmdExecutionStatus != null) {
            print("Command execution status code: ${cmdExecutionStatus}")
            debug("Automation_Stage_Name_${util.getPipelineSpecificStageName()}_${script}_Execution_Status")
            addStageCommands(script, cmdExecutionStatus)
            return cmdExecutionStatus
        }
    }

    // Method to add stage commands to global variables
    def addStageCommands(script, status) {
        if (!globalVars.stageCommands) {
            globalVars.stageCommands = [:]
        }

        if (!globalVars.stageCommands[util.getPipelineSpecificStageName()]) {
            globalVars.stageCommands[util.getPipelineSpecificStageName()] = []
        }

        def cmds = [:]
        cmds["script"] = script
        cmds["exitCode"] = status
        debug("cmds: ${cmds}")
        globalVars.stageCommands[util.getPipelineSpecificStageName()] << cmds
    }

    // Handle errors and log the details
    def handleError(Exception e) {
        e = ExceptionUtils.getRootCause(e)
        StackTraceUtils.deepSanitize(e)

        StringBuilder sb = new StringBuilder()

        if (params?.debug) {
            def sw = new StringWriter()
            def pw = new PrintWriter(sw)
            e.printStackTrace(pw)
            sb.append(sw.toString() + "\n")
        } else {
            sb.append("${e.getMessage()}\n")
        }

        if (e instanceof HorizonException) {
            // Handle HorizonException if needed
        }

        sb.append(e.errorMessages?.join("\n"))

        e = ExceptionUtils.getRootCause(e)
        StackTraceUtils.deepSanitize(e)

        while (e != null) {
            if (params?.debug) {
                def sw = new StringWriter()
                def pw = new PrintWriter(sw)
                e.printStackTrace(pw)
                sb.append(sw.toString() + "\n")
            } else {
                sb.append("${e.getMessage()}\n")
            }
            e = e.getCause()
        }

        String err = sb.toString()
        error(err)
    }

    // Debug method for logging
    void debug(String message) {
        if (params.debug) {
            echo ("DEBUG: ${message}")
        }
    }

    // Info logging method
    void info(String message) {
        echo ("INFO: ${message}")
    }

    // Print method for logging
    void print(String message) {
        echo (message)
    }

    // Error handling logging
    void error(String message) {
        message = maskMessage(message)
        cbj.error(message)
        printAutomationMessage(message, type: "ERROR")
    }

    // Mask secrets in messages
    String maskMessage(String message) {
        message = message.replaceAll(~/${globalVars.secrets?.join('|')}/, '****')
        return message
    }

    // Warn logging method
    void warn(String message) {
        String msg = "WARNING: ${message}"
        if (!globalVars.warnMsgs) {
            globalVars.warnMsgs = []
        }
        globalVars.warnMsgs << msg
        echo (msg)
    }

    // Echo messages
    void echo(String message) {
        message = maskMessage(message)
        cbj.echo(message)
    }

    // Execute command on node with a specific label
    void node(String label, Closure cls) {
        cbj.node(label, cls)
    }

    // Execute command within a workspace
    def ws(String path, Closure cls) {
        return cbj.ws(path, cls)
    }

    // Execute commands with timestamps
    def timestamps(Closure cls) {
        return cbj.timestamps(cls)
    }

    // Delete the directory
    def deleteDir() {
        return cbj.deleteDir()
    }

    // Check if the system is Unix
    def isUnix() {
        return cbj.isUnix()
    }

    // Clean the workspace
    def cleanWorkspace() {
        print("Cleaning workspace")
        try {
            cbj.steps.step([$class: 'WsCleanup', cleanWhenAborted: true, cleanWhenFailure: true, cleanWhenNotBuilt: true])
        } catch (ex) {
            util.throwExp(error: "Error cleaning workspace", ex)
        }
    }

    // Print automation message (customizable)
    void printAutomationMessage(String message, String type = "") {
        if (params.automationTestMsg) {
            def msg = message.split(regex: "\n").findAll { it.trim() != "" }.join("\n")
            echo ("Automation_${type}_${msg}")
        }
    }

    // Mask secrets for security purposes
    String mask(String secret) {
        if (!globalVars.secrets) {
            globalVars.secrets = []
        }
        if (!globalVars.secrets.contains(secret)) {
            globalVars.secrets << secret
        }
        return secret
    }
}
