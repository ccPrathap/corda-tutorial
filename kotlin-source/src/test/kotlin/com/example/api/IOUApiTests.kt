package com.example.api

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.IntegrationTestCategory
import net.corda.testing.driver.driver
import net.corda.testing.http.HttpApi
import org.bouncycastle.asn1.x500.X500Name
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.json.simple.JSONObject

class IOUApiTests : IntegrationTestCategory {

    @Test
    fun `run TradeTxApi`() {
        driver(isDebug = true) {
            val rpcUser = User("user", "password", permissions = setOf())

            val (controller, nodeA, nodeB) = Futures.allAsList(
                    startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type))),
                    startNode(ALICE.name, rpcUsers = listOf(rpcUser)),
                    startNode(BOB.name, rpcUsers = listOf(rpcUser))).getOrThrow()

            println("All nodes started")

            val (controllerAddr, nodeAAddr, nodeBAddr) = Futures.allAsList(
                    startWebserver(controller),
                    startWebserver(nodeA),
                    startWebserver(nodeB)).getOrThrow().map { it.listenAddress }

            println("All webservers started")

            val (_, nodeAApi, nodeBApi) = listOf(controller, nodeA, nodeB)
                    .zip(listOf(controllerAddr, nodeAAddr, nodeBAddr)).map {
                val mapper = net.corda.jackson.JacksonSupport.createDefaultMapper(it.first.rpc)

                HttpApi.fromHostAndPort(it.second, "api/example", mapper = mapper)
            }

            Assert.assertThat(getMe(nodeAApi), CoreMatchers.containsString(nodeA.nodeInfo.legalIdentity.name.toString()))
            Assert.assertThat(getPeers(nodeAApi), CoreMatchers.allOf(
                    CoreMatchers.containsString(nodeB.nodeInfo.legalIdentity.name.toString())
            ))

            Assert.assertThat(getMe(nodeBApi), CoreMatchers.containsString(nodeB.nodeInfo.legalIdentity.name.toString()))
            Assert.assertThat(getPeers(nodeBApi), CoreMatchers.allOf(
                    CoreMatchers.containsString(nodeA.nodeInfo.legalIdentity.name.toString())
            ))

            Assert.assertEquals(getTransactions(nodeAApi).size, 0)
            Assert.assertEquals(getTransactions(nodeBApi).size, 0)
        }
    }

    private fun getMe(nodeApi: HttpApi): String {
        println("Getting Me from ${nodeApi.root}")
        val me = nodeApi.getJson<Any>("me")
        return me.toString()
    }

    private fun getPeers(nodeApi: HttpApi): String {
        println("Getting peers from ${nodeApi.root}")
        val peers = nodeApi.getJson<Any>("peers")
        return peers.toString()
    }

    private fun getTransactions(nodeApi: HttpApi): Array<*> {
        println("Getting transactions from ${nodeApi.root}")
        val transactions = nodeApi.getJson<Array<*>>("ious")
        return transactions
    }
}
