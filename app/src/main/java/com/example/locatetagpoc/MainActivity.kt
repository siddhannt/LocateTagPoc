package com.example.locatetagpoc

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.zebra.rfid.api3.DYNAMIC_POWER_OPTIMIZATION
import com.zebra.rfid.api3.InvalidUsageException
import com.zebra.rfid.api3.OperationFailureException
import com.zebra.rfid.api3.RFIDReader
import com.zebra.rfid.api3.ReaderDevice
import com.zebra.rfid.api3.Readers
import com.zebra.rfid.api3.RfidEventsListener
import com.zebra.rfid.api3.RfidReadEvents
import com.zebra.rfid.api3.RfidStatusEvents
import com.zebra.rfid.api3.START_TRIGGER_TYPE
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE
import com.zebra.rfid.api3.SetAttribute
import com.zebra.rfid.api3.TAG_FIELD
import com.zebra.rfid.api3.TagStorageSettings
import com.zebra.rfid.api3.TriggerInfo
import com.zebra.scannercontrol.DCSSDKDefs
import com.zebra.scannercontrol.DCSScannerInfo
import com.zebra.scannercontrol.FirmwareUpdateEvent
import com.zebra.scannercontrol.IDcsSdkApiDelegate
import com.zebra.scannercontrol.SDKHandler


class MainActivity : AppCompatActivity() {
    // Declare RFID reader variable

    private var readers: Readers?=null
    private lateinit var availableRFIDReaderList: ArrayList<ReaderDevice>
    private lateinit var readerDevice: ReaderDevice
    private  var reader: RFIDReader? = null
    private val TAG = "DEMO"

    private lateinit var tagIdInput: EditText
    private lateinit var locateTagButton: Button
    private lateinit var connectButton: Button
    private lateinit var locationResult: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var dropdownSpinner: Spinner

    private var eventHandler: EventHandler?= null
    var tagsToReadCount: Int = 1000
    var isSingleReadMode: Boolean = true
    private lateinit var sdkHandler:SDKHandler
    var tagidStore = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sdkHandler = SDKHandler(this)

        // UI Elements
        tagIdInput = findViewById(R.id.tagIdInput)
        locateTagButton = findViewById(R.id.locateTagButton)
        locationResult = findViewById(R.id.locationResult)
        connectButton = findViewById(R.id.connectButton)
        //progressBar = findViewById(R.id.proximityProgressBar)
        eventHandler = EventHandler()
        dropdownSpinner = findViewById(R.id.dropdownSpinner)



        connectButton.setOnClickListener {
            var notificationMask = 0
            notificationMask =
                notificationMask or (DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value)
            notificationMask =
                notificationMask or (DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value)
            notificationMask = notificationMask or DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value

            sdkHandler.dcssdkSubsribeForEvents(notificationMask)
            sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL)
            sdkHandler.dcssdkEnableAvailableScannersDetection(true)
            sdkHandler.dcssdkGetActiveScannersList(emptyList())
            sdkHandler.dcssdkSetDelegate(EventHandler())
            connectRFIDReader()
        }

        // Set up button click listener
        locateTagButton.setOnClickListener {
            val tagId = tagIdInput.text.toString()
            tagidStore = tagId
            if (tagId.isNotEmpty()) {
                locateTag(tagId)
            } else {
                Toast.makeText(this, "Please enter a Tag ID", Toast.LENGTH_SHORT).show()
            }
        }

        val items = arrayOf("low", "optimal", "high")

        setupDropdownSpinner(items, dropdownSpinner)
    }

    private fun setupDropdownSpinner(items: Array<String>, dropdownSpinner: Spinner) {
        // Create ArrayAdapter for Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        dropdownSpinner.adapter = adapter
        // Spinner selection listener
        dropdownSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                if (reader != null) {
                    reader?.Actions?.TagLocationing?.Stop()
                    updateAntennaPower(selectedItem)
                    locateTag(tagidStore)
                }
                Toast.makeText(this@MainActivity, "Selected: $selectedItem", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle the case when nothing is selected
            }
        }
    }



    private fun connectRFIDReader() {
        Log.d(TAG, "Connecting to RFID Reader")
        if( readers == null) {
            readers = Readers()
        }
        // Running on diff thread other than UI thread

            try {
                availableRFIDReaderList = readers?.GetAvailableRFIDReaderList() as ArrayList<ReaderDevice>
                Log.d(TAG, "Available Readers: ${availableRFIDReaderList.size}")
                if (availableRFIDReaderList.isNotEmpty()) {
                    readerDevice = availableRFIDReaderList[0]
                    reader = readerDevice.rfidReader
                    if (reader?.isConnected == false) {
                        reader?.connect()
                        Log.d(TAG, "Connecting to reader...")
                        if (reader?.isConnected == true) {
                            configureReader()
                            Log.d(TAG, "Connected to RFID Reader")

                        } else {
                            Log.d(TAG, "Failed to connect to RFID Reader")
                            runOnUiThread {
                                Toast.makeText(this, "Failed to connect RFID Reader", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No RFID Reader found")
                    runOnUiThread {
                        Toast.makeText(this, "No RFID Reader found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to RFID Reader: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Error connecting to Rfid: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

    }

    private fun updateAntennaPower(selectedItem: String) {
        // Check if the RFID reader is initialized
        Thread {
            if (reader?.isConnected == false) {
                Toast.makeText(this, "RFID Reader not connected", Toast.LENGTH_SHORT).show()
                return@Thread
            }
            val powerIndex = when (selectedItem) {
                "low" -> 100
                "optimal" -> 200
                "high" -> 300
                else -> 300 // Default to high if anything goes wrong
            }

            // Set the antenna power
            val antennas = reader?.Config?.Antennas?.availableAntennas
        if (antennas != null) {
            for (index in antennas) {
                val antennaRfConfig = reader?.Config?.Antennas?.getAntennaRfConfig(index.toInt())
                antennaRfConfig?.transmitPowerIndex = powerIndex // Set power index
                reader?.Config?.Antennas?.setAntennaRfConfig(index.toInt(), antennaRfConfig)
                Log.d(TAG, "Set power index: $powerIndex for antenna $index")
            }
        }
        }
    }

    private fun setDPO(bEnable: Boolean) {
        Log.d(TAG, "setDPO $bEnable")
        try {
            // control the DPO
            reader?.Config?.dpoState =
                if (bEnable) DYNAMIC_POWER_OPTIMIZATION.ENABLE else DYNAMIC_POWER_OPTIMIZATION.DISABLE
        } catch (e: InvalidUsageException) {
            e.printStackTrace()
        } catch (e: OperationFailureException) {
            e.printStackTrace()
        }
    }
    private fun configureReader() {
        if (reader?.isConnected == true) {
            try {
               setDPO(false)
                // Set Event Handlers
                reader?.Events?.setHandheldEvent(true) // Assuming handheldEvent is enabled
                reader?.Events?.setTagReadEvent(true)
                reader?.Events?.setReaderDisconnectEvent(true)
                reader?.Events?.setBatteryEvent(true)
                reader?.Events?.setInventoryStopEvent(true)
                reader?.Events?.setInventoryStartEvent(true)
                reader?.Events?.setPowerEvent(true)
                reader?.Events?.addEventsListener(eventHandler)

                // Set antenna configurations
                val antennas = reader?.Config?.Antennas?.availableAntennas
                reader?.Config?.setAccessOperationWaitTimeout(10000) // 10-second timeout

                if (antennas != null) {
                    Log.d(TAG, "Number of available antennas: ${antennas.size}")
                    for (index in antennas) {
                        val antennaRfConfig = reader?.Config?.Antennas?.getAntennaRfConfig(index.toInt())
//                        antennaRfConfig?.transmitPowerIndex = PowerConstant.readPower
                        reader?.Config?.Antennas?.setAntennaRfConfig(index.toInt(), antennaRfConfig)
                        Log.d(TAG, "Antenna Power Index: ${antennaRfConfig?.transmitPowerIndex} for antenna $index")
                    }
                }

                // Set additional attribute
                val setAttributeInfo = SetAttribute().apply {
                    attnum = 1664
                    atttype = "B"
                    attvalue = "0"
                }
                reader?.Config?.setAttribute(setAttributeInfo)

                // Configure tag storage settings
                val tagStorageSettings = TagStorageSettings().apply {
                    tagFields = arrayOf(
                        TAG_FIELD.PC,
                        TAG_FIELD.TAG_SEEN_COUNT,
                        TAG_FIELD.CRC,
                        TAG_FIELD.PEAK_RSSI
                    )
                }
                reader?.Config?.tagStorageSettings = tagStorageSettings

                // Configure triggers
                val triggerInfo = TriggerInfo().apply {
                    StartTrigger.triggerType = START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE
                    StopTrigger.triggerType = STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_TAG_OBSERVATION_WITH_TIMEOUT
                    StopTrigger.TagObservation.n = getNoOfTagToRead().toShort()

                    if (isSingleReadMode) {
                        StopTrigger.TagObservation.timeout = 3000 // 3 seconds for single read mode
                    } else {
                        StopTrigger.TagObservation.timeout = 0 // Continuous scanning
                    }
                }

                reader?.Config?.startTrigger = triggerInfo.StartTrigger
                reader?.Config?.stopTrigger = triggerInfo.StopTrigger

                Log.d(TAG, "Reader configuration complete.")
            } catch (e: InvalidUsageException) {
                Log.e(TAG, "Invalid usage exception: ${e.message}")
            } catch (e: OperationFailureException) {
                Log.e(TAG, "Operation failure: ${e.message}")
            }
        }
    }

    fun getNoOfTagToRead(): Int {
        if (isSingleReadMode) return 1
        return tagsToReadCount
    }




//    private fun configureReader() {
//        if (reader.isConnected) {
//            val triggerInfo = TriggerInfo()
//            triggerInfo.StartTrigger.triggerType = START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE
//            triggerInfo.StopTrigger.triggerType = STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE
//            try {
//
//                reader.Events.addEventsListener(eventHandler)
//                // receive events from reader
//                reader.Events?.setHandheldEvent(true)
//                reader.Events?.setTagReadEvent(true)
//                reader.Events?.setReaderDisconnectEvent(true)
//                reader.Events?.setPowerEvent(false)
//
//                // set trigger mode as rfid so scanner beam will not come
//                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true)
//                // set start and stop triggers
//                reader.Config.startTrigger = triggerInfo.StartTrigger
//                reader.Config.stopTrigger = triggerInfo.StopTrigger
//            } catch (e: InvalidUsageException) {
//                e.printStackTrace()
//            } catch (e: OperationFailureException) {
//                e.printStackTrace()
//            }
//        }
//    }

    private fun locateTag(tagId: String) {
        // Check if the RFID reader is initialized
        if (reader?.isConnected == false) {
            Toast.makeText(this, "RFID Reader not connected", Toast.LENGTH_SHORT).show()
            return
        }

            try {
                // Start locating the tag
                reader?.Actions?.TagLocationing?.Perform(tagId, null, null)

                // Wait for a few seconds to allow the reader to locate the tag
                Thread.sleep(5000)
                Log.d(TAG, "Location:")
                // Stop locating the tag
                // reader.Actions.TagLocationing.Stop()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error locating tag: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }

    inner class EventHandler : RfidEventsListener , IDcsSdkApiDelegate  {
        override fun eventReadNotify(e: RfidReadEvents) {
            val myTags = reader?.Actions?.getReadTags(100)
            if (myTags != null) {
                for (tag in myTags) {
                    Log.d(TAG, "Tag ID: ${tag.tagID}")
                    if (tag.isContainsLocationInfo) {
                        val distance = tag.LocationInfo.relativeDistance
                        Log.d(TAG, "Tag locationing distance: $distance")
                        runOnUiThread {
                            locationResult.text = "Distance: $distance%"
                            // Update progress bar to reflect the distance (0 to 100%)

                            val proximityProgressBar = findViewById<ProgressBar>(R.id.proximityProgressBar)
                            val distanceText = findViewById<TextView>(R.id.distanceText)

                            proximityProgressBar.progress =
                                distance.toInt()  // Update progress (0 to 100)
                            distanceText.text = "$distance%"


                            Log.d(TAG, " Distance: $distance")
                        }
                    }
                }
            }
        }

        override fun eventStatusNotify(statusEvent: RfidStatusEvents) {
            // Handle other status notifications if necessary
        }

        override fun dcssdkEventScannerAppeared(p0: DCSScannerInfo?) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventScannerDisappeared(p0: Int) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventCommunicationSessionEstablished(p0: DCSScannerInfo?) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventCommunicationSessionTerminated(p0: Int) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventBarcode(p0: ByteArray?, p1: Int, p2: Int) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventImage(p0: ByteArray?, p1: Int) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventVideo(p0: ByteArray?, p1: Int) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventBinaryData(p0: ByteArray?, p1: Int) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventFirmwareUpdate(p0: FirmwareUpdateEvent?) {
            TODO("Not yet implemented")
        }

        override fun dcssdkEventAuxScannerAppeared(p0: DCSScannerInfo?, p1: DCSScannerInfo?) {
            TODO("Not yet implemented")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (reader?.isConnected == true) {
                reader?.Events?.removeEventsListener(eventHandler)
                reader?.disconnect()
                Toast.makeText(applicationContext, "Disconnecting reader", Toast.LENGTH_LONG).show()
                readers?.Dispose()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}