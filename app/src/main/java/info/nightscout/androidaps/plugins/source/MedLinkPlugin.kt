package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.db.CalibrationFactorReading
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.SensorDataReading
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedLinkPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    // private val mainApp: MainApp,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val nsUpload: NSUpload,
    config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .pluginIcon(R.drawable.ic_minilink)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.medlink_app_patched)
    .shortName(R.string.dexcom_short)
    .preferencesId(R.xml.pref_bgsourcemedlink)
    .description(R.string.description_source_dexcom),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    init {
        if (!config.NSCLIENT) {
            pluginDescription.setDefault()
        }
    }

    override fun advancedFilteringSupported(): Boolean {
        return true
    }

    // override fun onStart() {
    //     super.onStart()
    //     if (ContextCompat.checkSelfPermission(mainApp, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
    //         val intent = Intent(mainApp, RequestDexcomPermissionActivity::class.java)
    //         intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    //         mainApp.startActivity(intent)
    //     }
    // }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        try {
            val sensorType = intent.getStringExtra("sensorType") ?: ""
            val glucoseValues = intent.getBundleExtra("glucoseValues")
            val isigValues = intent.getBundleExtra("isigValues")
            for (i in 0 until glucoseValues.size()) {
                glucoseValues.getBundle(i.toString())?.let { glucoseValue ->
                    val bgReading = BgReading()
                    bgReading.value = glucoseValue.getDouble("value")
                    bgReading.direction = glucoseValue.getString("direction")
                    bgReading.date = glucoseValue.getLong("date")
                    bgReading.raw = 0.0
                    val dbHelper = MainApp.getDbHelper()
                    // aapsLogger.info(LTag.DATABASE, "bgneedupdate? "+dbHelper.thisBGNeedUpdate(bgReading))
                    if (dbHelper.thisBGNeedUpdate(bgReading)) {
                        if (dbHelper.createIfNotExists(bgReading, "MedLink$sensorType")) {
                            if (sp.getBoolean(R.string.key_medlink_nsupload, false) &&
                                (isigValues == null || isigValues.size() == 0)) {
                                nsUpload.uploadBg(bgReading, "AndroidAPS-MedLink$sensorType")
                            }
                            if (sp.getBoolean(R.string.key_medlink_xdripupload, false)) {
                                nsUpload.sendToXdrip(bgReading)
                            }
                        }
                    }
                }
            }
            if (isigValues != null) {
                for (i in 0 until isigValues.size()) {
                    isigValues.getBundle(i.toString())?.let { isigValue ->
                        val dbHelper = MainApp.getDbHelper()

                        val bgReading = BgReading()
                        bgReading.value = isigValue.getDouble("value")
                        bgReading.direction = isigValue.getString("direction")
                        bgReading.date = isigValue.getLong("date")
                        bgReading.raw = 0.0

                        val sensReading = SensorDataReading()
                        sensReading.bgValue = isigValue.getDouble("value")
                        sensReading.direction = isigValue.getString("direction")
                        sensReading.date = isigValue.getLong("date")
                        sensReading.isig = isigValue.getDouble("isig")
                        sensReading.deltaSinceLastBG = isigValue.getDouble("delta")
                        sensReading.sensorUptime = dbHelper.sensorAge
                        sensReading.bgReading = bgReading
                        if(isigValue.getDouble("calibrationFactor") == 0.0){
                            sensReading.calibrationFactor = dbHelper.getCalibrationFactor(sensReading.date)?.calibrationFactor ?: 0.0
                        }else {
                            sensReading.calibrationFactor = isigValue.getDouble("calibrationFactor")
                        }
                        // aapsLogger.info(LTag.DATABASE, "bgneedupdate? "+dbHelper.thisBGNeedUpdate(bgReading))
                        if (dbHelper.thisSensNeedUpdate(sensReading)) {
                            if (dbHelper.createIfNotExists(sensReading, "MedLink$sensorType")) {
                                if (sp.getBoolean(R.string.key_medlink_nsupload, false)) {
                                    //
                                    nsUpload.uploadEnliteData(sensReading, "AndroidAPS-MedLink$sensorType")
                                }
                                if (sp.getBoolean(R.string.key_medlink_xdripupload, false)) {
                                    nsUpload.sendToXdrip(sensReading.bgReading)
                                }
                            }
                        }
                        val calibrationFactor = CalibrationFactorReading()
                        calibrationFactor.date = isigValue.getLong("date")
                        calibrationFactor.calibrationFactor = isigValue.getDouble("calibrationFactor")
                        dbHelper.createIfNotExists(calibrationFactor, "MedLink$sensorType")
                    }
                }
            }
            val meters = intent.getBundleExtra("meters")
            for (i in 0 until meters.size()) {
                val meter = meters.getBundle(i.toString())
                meter?.let {
                    val timestamp = it.getLong("timestamp")
                    aapsLogger.info(LTag.DATABASE, "meters timestamp $timestamp")
                    val now = DateUtil.now()
                    if (timestamp > now - T.months(1).msecs() && timestamp < now)
                        if (MainApp.getDbHelper().getCareportalEventFromTimestamp(timestamp) == null) {
                            val jsonObject = JSONObject()
                            jsonObject.put("enteredBy", "AndroidAPS-MedLink$sensorType")
                            jsonObject.put("created_at", DateUtil.toISOString(timestamp))
                            jsonObject.put("eventType", CareportalEvent.BGCHECK)
                            jsonObject.put("glucoseType", "Finger")
                            jsonObject.put("glucose", meter.getDouble("meterValue"))
                            jsonObject.put("units", Constants.MGDL)
                            jsonObject.put("mills", timestamp)

                            val careportalEvent = CareportalEvent(injector)
                            careportalEvent.date = timestamp
                            careportalEvent.source = Source.USER
                            careportalEvent.eventType = CareportalEvent.BGCHECK
                            careportalEvent.json = jsonObject.toString()
                            MainApp.getDbHelper().createOrUpdate(careportalEvent)
                            nsUpload.uploadCareportalEntryToNS(jsonObject)
                        }
                }
            }

            if (sp.getBoolean(R.string.key_medlink_lognssensorchange, false) && intent.hasExtra("sensorInsertionTime")) {
                intent.extras?.let {
                    val sensorInsertionTime = it.getLong("sensorInsertionTime") * 1000
                    val now = DateUtil.now()
                    if (sensorInsertionTime > now - T.months(1).msecs() && sensorInsertionTime < now)
                        if (MainApp.getDbHelper().getCareportalEventFromTimestamp(sensorInsertionTime) == null) {
                            val jsonObject = JSONObject()
                            jsonObject.put("enteredBy", "AndroidAPS-MedLink$sensorType")
                            jsonObject.put("created_at", DateUtil.toISOString(sensorInsertionTime))
                            jsonObject.put("eventType", CareportalEvent.SENSORCHANGE)
                            val careportalEvent = CareportalEvent(injector)
                            careportalEvent.date = sensorInsertionTime
                            careportalEvent.source = Source.USER
                            careportalEvent.eventType = CareportalEvent.SENSORCHANGE
                            careportalEvent.json = jsonObject.toString()
                            MainApp.getDbHelper().createOrUpdate(careportalEvent)
                            nsUpload.uploadCareportalEntryToNS(jsonObject)
                        }
                }
            }
        } catch (e: Exception) {
            aapsLogger.error("Error while processing intent from MedLink App", e)
        }
    }

}
