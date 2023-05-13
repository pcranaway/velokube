package io.github.pcranaway.velokube

import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import com.moandjiezana.toml.Toml
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import java.lang.reflect.Type
import java.net.InetSocketAddress
import java.nio.file.Files.copy
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@Plugin(
    id = "velokube",
    name = "Velokube",
    version = "1.0-SNAPSHOT",
    authors = ["pcranaway"],
    description = "Synchronizes k8s-deployed Minecraft servers with Velocity"
)
class VelokubePlugin @Inject constructor(
    var server: ProxyServer,
    var logger: Logger,
    @DataDirectory var dataDirectory: Path
)
{

    lateinit var config: Toml

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        loadConfig()
        kubeWatch()
    }

    /**
     * Subscribes to Kubernetes and watches for pod updates
     */
    fun kubeWatch() {
        // read namespace from config
        val namespace = config.getString("namespace")

        // connect to kubernetes api
        val client = Config.defaultClient()
        Configuration.setDefaultApiClient(client)

        val api = CoreV1Api(client)

        // watch for pod updates
        val watch: Watch<V1Pod> = Watch.createWatch(
            client,
            api.listNamespacedPodCall(namespace, null, null, null, null, null, null, null, null, null, true, null),
            object : TypeToken<Watch.Response<V1Pod>>() {}.type
        )

        watch.use { watch ->
            watch.forEach { event ->
                when (event.type) {
                    "MODIFIED" -> handlePod(event.`object` as V1Pod)
                    "ADDED" -> handlePod(event.`object` as V1Pod)
                    "DELETED" -> handleDeleted(event.`object` as V1Pod)
                }
            }
        }

    }

    /**
     * Handles the creation or modification of a pod
     */
    fun handlePod(pod: V1Pod)  {
        val app = pod.metadata?.labels?.get("app")

        if(app != "server") {
            return
        }

        val name = pod.metadata!!.name!!

        // if the server is already registered, don't do anything
        if(!this.server.matchServer(app).isEmpty()) {
            return
        }

        pod.status?.podIP.let {
            // register server
            val server = ServerInfo(
                name,
                InetSocketAddress(pod.status!!.podIP, 25565)
            )

            this.server.registerServer(server)

            logger.info("registered server $server")
        }
    }

    /**
     * Handles the deletion of a pod
     */
    fun handleDeleted(pod: V1Pod) {
        val server = this.server.getServer(pod.metadata!!.name).get().serverInfo

        this.server.unregisterServer(server)

        logger.info("unregistered server $pod")
    }

    /**
     * Creates the configuration directory and file and loads it
     */
    fun loadConfig() {
        // create directory if it doesn't exist
        if(!dataDirectory.exists()) {
            dataDirectory.createDirectories()
        }

        // load default config.toml if it doesn't exist
        val configPath: Path = dataDirectory.resolve("config.toml")

        if(!configPath.exists()) {
            val resource = this.javaClass.getResourceAsStream("/config.toml")

            copy(resource, configPath.toAbsolutePath())
        }

        // load config
        this.config = Toml().read(configPath.toFile())
    }

}