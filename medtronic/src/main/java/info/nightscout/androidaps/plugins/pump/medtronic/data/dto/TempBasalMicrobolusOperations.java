package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.gson.annotations.Expose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractSequentialList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import info.nightscout.androidaps.logging.L;

public class TempBasalMicrobolusOperations {

    private boolean shouldBeSuspended;
    private int durationInMinutes;
    private double absoluteRate;

//    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    private LinkedBlockingDeque<TempBasalMicroBolusPair> operations = new LinkedBlockingDeque<>();
    @Expose
    private Integer remainingOperations = operations.size();
    @Expose
    private double totalDosage = 0d;
    @Expose
    private Integer nextOperationInterval = 0;
    @Expose
    private Integer suspendedTime;

    public TempBasalMicrobolusOperations() {

    }

    public TempBasalMicrobolusOperations(Integer remainingOperations, double totalDosage, 
                                         int durationInMinutes,
                                         LinkedBlockingDeque<TempBasalMicroBolusPair> operations) {
        this.remainingOperations = remainingOperations;
        this.totalDosage = totalDosage;
        this.operations = operations;
        this.durationInMinutes = durationInMinutes;
    }

    public Integer getRemainingOperations() {
        return remainingOperations;
    }

    public double getTotalDosage() {
        return totalDosage;
    }

    public LinkedBlockingDeque<TempBasalMicroBolusPair> getOperations() {
        return operations;
    }

    @Override
    public String toString() {
        return "TempBasalMicrobolusOperations{" +
                "remainingOperations=" + remainingOperations +
                ", operationDose=" + totalDosage +
                ", nextOperationInterval=" + nextOperationInterval +
                ", operations=" + operations +
                '}';
    }

    public synchronized void updateOperations(Integer remainingOperations,
                                              double operationDose,
                                              LinkedBlockingDeque<TempBasalMicroBolusPair> operations,
                                              Integer suspendedTime) {
        this.remainingOperations = remainingOperations;
        this.suspendedTime = suspendedTime;
        this.totalDosage = operationDose;
        this.operations = operations;
    }

    public synchronized void clearOperations() {
        this.operations.clear();
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public void setDurationInMinutes(Integer durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    public double getAbsoluteRate() {
        return absoluteRate;
    }

    public void setAbsoluteRate(double absoluteRate) {
        this.absoluteRate = absoluteRate;
    }

    public void setShouldBeSuspended(boolean suspended) {
        this.shouldBeSuspended = suspended;
    }

    public void setOperations(LinkedBlockingDeque<TempBasalMicroBolusPair> operations) {
        this.operations = operations;
    }
}
