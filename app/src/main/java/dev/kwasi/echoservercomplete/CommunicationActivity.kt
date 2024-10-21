package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.IntentFilter
import android.health.connect.datatypes.units.Length
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.chatlist.ChatListAdapter
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter:PeerListAdapter? = null
    private var chatListAdapter:ChatListAdapter? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var client: Client? = null
    private var deviceIp: String = ""
    private lateinit var etStudentID : EditText
    private lateinit var searchBtn : Button
    private lateinit var rvPeerList : RecyclerView
    private lateinit var etMessage : EditText
    private lateinit var sendBtn : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        registerReceiver(wfdManager,intentFilter)


        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)


        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)

        peerListAdapter = PeerListAdapter(this)
        rvPeerList = findViewById(R.id.rvPeerList)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)
        rvPeerList.visibility = View.GONE

        etStudentID = findViewById(R.id.etStudentID)
        searchBtn = findViewById(R.id.SearchClassesBtn)
        etMessage = findViewById(R.id.etMessage)
        sendBtn = findViewById(R.id.sendBtn)

        searchBtn.setOnClickListener{ view : View ->
            val studentID = etStudentID.text.toString()
            if (studentID.isNotEmpty()) {
                discoverNearbyPeers(view)
            }
            else{
                val toast = Toast.makeText(this, "Please enter a Student ID", Toast.LENGTH_SHORT)
                toast.show()
            }
        }

        sendBtn.setOnClickListener { view: View ->
            val message = etMessage.text.toString()
            if (message.isNotEmpty()) {
                val content = ContentModel(message, deviceIp)
                client?.sendMessage(content)
                etMessage.setText("")
            } else {
                val toast = Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT)
                toast.show()
            }
        }


    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        wfdManager?.also {
            unregisterReceiver(it)
        }
    }


    fun discoverNearbyPeers(view: View) {
        var text = "Discovering peers"
        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()
        wfdManager?.discoverPeers()
    }


    private fun updateUI(){
        //The rules for updating the UI are as follows:
        // IF the WFD adapter is NOT enabled then
        //      Show UI that says turn on the wifi adapter
        // ELSE IF there is NO WFD connection then i need to show a view that allows the user to either
        // 1) create a group with them as the group owner OR
        // 2) discover nearby groups
        // ELSE IF there are nearby groups found, i need to show them in a list
        // ELSE IF i have a WFD connection i need to show a chat interface where i can send/receive messages
        val wfdAdapterErrorView:ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE

        val wfdNoConnectionView:ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE

        rvPeerList = findViewById(R.id.rvPeerList)
        rvPeerList.visibility = if (wfdAdapterEnabled && !wfdHasConnection && hasDevices) View.VISIBLE else View.GONE

        val wfdConnectedView:ConstraintLayout = findViewById(R.id.clHasConnection)
        wfdConnectedView.visibility = if(wfdHasConnection)View.VISIBLE else View.GONE
    }


    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        var text = "There was a state change in the WiFi Direct. Currently it is "
        text = if (isEnabled){
            "$text enabled!"
        } else {
            "$text disabled! Try turning on the WiFi adapter"
        }

        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()
        updateUI()
    }


    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        val toast = Toast.makeText(this, "Updated listing of nearby WiFi Direct devices", Toast.LENGTH_SHORT)
        toast.show()
        hasDevices = deviceList.isNotEmpty()
        peerListAdapter?.updateList(deviceList)
        rvPeerList = findViewById(R.id.rvPeerList)
        rvPeerList.visibility = View.VISIBLE
        updateUI()
    }


    override fun onGroupStatusChanged(groupInfo: WifiP2pGroup?) {
        val text = if (groupInfo == null) {
            "Group is not formed"
        } else {
            "Group has been formed"
        }
        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()


        if (groupInfo == null) {
            client?.close()
        } else if (!groupInfo.isGroupOwner && client == null) {
            client = Client(this)
            deviceIp = client!!.ip
        }else if (groupInfo.isGroupOwner){
            Log.i("CommunicationActivity", "Is the group owner")
        }

        val studentID = etStudentID.text.toString()
        val content = ContentModel(message = studentID, senderIp = deviceIp)
        client?.sendMessage(content)


        wfdHasConnection = groupInfo != null
    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        val toast = Toast.makeText(this, "Device parameters have been updated" , Toast.LENGTH_SHORT)
        toast.show()
    }



    override fun onPeerClicked(peer: WifiP2pDevice) {

        wfdManager?.connectToPeer(peer)
        updateUI()
        val toast = Toast.makeText(this, "Peer has been clicked", Toast.LENGTH_SHORT)
        toast.show()

        wfdManager?.requestGroupInfo { group ->
            if (group != null) {
                Log.i("Student", "Connecting to lecturer (GO)")
                wfdManager?.connectToPeer(peer)
                updateUI()
            } else {
                Log.i("Student", "Lecturer is not the GO, cannot connect")
            }
        }

    }


    override fun onContent(content: ContentModel) {
        runOnUiThread{
            chatListAdapter?.addItemToEnd(content)

            val rvChatList: RecyclerView = findViewById(R.id.rvChat)
            rvChatList.adapter = chatListAdapter
            rvChatList.layoutManager = LinearLayoutManager(this)
            rvChatList.scrollToPosition(chatListAdapter!!.itemCount - 1)
        }
    }

}