package com.example.tourify;

public class TourifyEvent {
    private String mId;
    private String mEventName;
    private long mStartTime;
    private long mEndTime;

    TourifyEvent(String id, String eventName, long startTime, long endTime) {
        mId = id;
        mEventName = eventName;
        mStartTime = startTime;
        mEndTime = endTime;
    }

    public String getId() {
        return mId;
    }

    public void setId(String mId) {
        this.mId = mId;
    }

    public String getEventName() {
        return mEventName;
    }

    public void setEventName(String mEventName) {
        this.mEventName = mEventName;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public void setStartTime(long mStartTime) {
        this.mStartTime = mStartTime;
    }

    public long getEndTime() {
        return mEndTime;
    }

    public void setEndTime(long endTime) {
        this.mEndTime = endTime;
    }
}
