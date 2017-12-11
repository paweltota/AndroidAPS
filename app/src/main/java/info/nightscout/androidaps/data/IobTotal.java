package info.nightscout.androidaps.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Round;

public class IobTotal {
    private static Logger log = LoggerFactory.getLogger(IobTotal.class);

    public double iob;
    public double activity;
    public double bolussnooze;
    public double basaliob;
    public double netbasalinsulin;
    public double hightempinsulin;

    // oref1
    public double microBolusInsulin;
    public double microBolusIOB;
    public long lastBolusTime;
    public long lastTempDate;
    public int lastTempDuration;
    public double lastTempRate;
    public IobTotal iobWithZeroTemp;

    public double netInsulin = 0d; // for calculations from temp basals only
    public double netRatio = 0d; // net ratio at start of temp basal

    public double extendedBolusInsulin = 0d; // total insulin for extended bolus

    long time;


    public IobTotal clone() {
        IobTotal copy = new IobTotal(time);
        copy.iob = iob;
        copy.activity = activity;
        copy.bolussnooze = bolussnooze;
        copy.basaliob = basaliob;
        copy.netbasalinsulin = netbasalinsulin;
        copy.hightempinsulin = hightempinsulin;
        copy.microBolusInsulin = microBolusInsulin;
        copy.microBolusIOB = microBolusIOB;
        copy.lastBolusTime = lastBolusTime;
        copy.lastTempDate = lastTempDate;
        copy.lastTempDuration = lastTempDuration;
        copy.lastTempRate = lastTempRate;
        copy.iobWithZeroTemp = iobWithZeroTemp;
        return copy;
    }

    public IobTotal(long time) {
        this.iob = 0d;
        this.activity = 0d;
        this.bolussnooze = 0d;
        this.basaliob = 0d;
        this.netbasalinsulin = 0d;
        this.hightempinsulin = 0d;
        this.microBolusInsulin = 0d;
        this.microBolusIOB = 0d;
        this.lastBolusTime = 0;
        this.time = time;
    }

    public IobTotal plus(IobTotal other) {
        iob += other.iob;
        activity += other.activity;
        bolussnooze += other.bolussnooze;
        basaliob += other.basaliob;
        netbasalinsulin += other.netbasalinsulin;
        hightempinsulin += other.hightempinsulin;
        netInsulin += other.netInsulin;
        extendedBolusInsulin += other.extendedBolusInsulin;
        microBolusInsulin += other.microBolusInsulin;
        microBolusIOB += other.microBolusIOB;
        return this;
    }

    public static IobTotal combine(IobTotal bolusIOB, IobTotal basalIob) {
        IobTotal result = new IobTotal(bolusIOB.time);
        result.iob = bolusIOB.iob + basalIob.basaliob;
        result.activity = bolusIOB.activity + basalIob.activity;
        result.bolussnooze = bolusIOB.bolussnooze;
        result.basaliob = bolusIOB.basaliob + basalIob.basaliob;
        result.netbasalinsulin = bolusIOB.netbasalinsulin + basalIob.netbasalinsulin;
        result.hightempinsulin = basalIob.hightempinsulin + bolusIOB.hightempinsulin;
        result.microBolusInsulin = bolusIOB.microBolusInsulin + basalIob.microBolusInsulin;
        result.microBolusIOB = bolusIOB.microBolusIOB + basalIob.microBolusIOB;
        result.lastBolusTime = bolusIOB.lastBolusTime;
        result.lastTempDate = basalIob.lastTempDate;
        result.lastTempRate = basalIob.lastTempRate;
        result.lastTempDuration = basalIob.lastTempDuration;
        result.iobWithZeroTemp = basalIob.iobWithZeroTemp;
        return result;
    }

    public IobTotal round() {
        this.iob = Round.roundTo(this.iob, 0.001);
        this.activity = Round.roundTo(this.activity, 0.0001);
        this.bolussnooze = Round.roundTo(this.bolussnooze, 0.0001);
        this.basaliob = Round.roundTo(this.basaliob, 0.001);
        this.netbasalinsulin = Round.roundTo(this.netbasalinsulin, 0.001);
        this.hightempinsulin = Round.roundTo(this.hightempinsulin, 0.001);
        this.microBolusInsulin = Round.roundTo(this.microBolusInsulin, 0.001);
        this.microBolusIOB = Round.roundTo(this.microBolusIOB, 0.001);
        return this;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("basaliob", basaliob);
            json.put("activity", activity);
            json.put("time", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return json;
    }

    public JSONObject determineBasalJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("basaliob", basaliob);
            json.put("bolussnooze", bolussnooze);
            json.put("activity", activity);
            json.put("lastBolusTime", lastBolusTime);
            json.put("time", DateUtil.toISOString(new Date(time)));
            /*

            This is requested by SMB determine_basal but by based on Scott's info
            it's MDT specific safety check only
            It's causing rounding issues in determine_basal

            JSONObject lastTemp = new JSONObject();
            lastTemp.put("date", lastTempDate);
            lastTemp.put("rate", lastTempRate);
            lastTemp.put("duration", lastTempDuration);
            json.put("lastTemp", lastTemp);
            */
            if (iobWithZeroTemp != null) {
                JSONObject iwzt = iobWithZeroTemp.determineBasalJson();
                json.put("iobWithZeroTemp", iwzt);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return json;
    }

}
