package com.pixelfleet.task.domain;

public enum TaskPriority {
    LOW(0),
    NORMAL(10),
    HIGH(20),
    URGENT(30);

    private final int weight;

    TaskPriority(int weight) {
        this.weight = weight;
    }

    /** Higher weight is dispatched first by the assignment policy. */
    public int getWeight() {
        return weight;
    }
}
