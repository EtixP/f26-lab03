package edu.cmu.cs214.roomreserve;

import java.util.List;

/**
 * Entry point for booking requests. Takes strings, returns strings.
 */
public class RequestHandler {

    private final InMemoryStore store = new InMemoryStore();

    public String createBooking(String room, String date, String start, String end, String user) {
        String[] startParts = start.split(":");
        String[] endParts = end.split(":");
        if (startParts.length != 2 || endParts.length != 2) {
            return "ERROR: time must look like HH:MM";
        }
        long startMinutes;
        long endMinutes;
        try {
            startMinutes = Long.parseLong(startParts[0]) * 60 + Long.parseLong(startParts[1]);
            endMinutes = Long.parseLong(endParts[0]) * 60 + Long.parseLong(endParts[1]);
        } catch (NumberFormatException e) {
            return "ERROR: time must look like HH:MM";
        }
        if (endMinutes <= startMinutes) {
            return "ERROR: end must be after start";
        }

        List<long[]> existing = store.slotsFor(room, date);
        for (long[] slot : existing) {
            if (startMinutes < slot[1] && slot[0] < endMinutes) {
                return "ERROR: " + room + " is already booked between "
                        + minutesToText(slot[0]) + " and " + minutesToText(slot[1]);
            }
        }

        boolean added = store.addSlot(room, date, startMinutes, endMinutes, user);
        if (!added) {
            return "ERROR: that booking already exists";
        }
        return "OK: booked " + room + " on " + date + " " + start + " to " + end + " for " + user;
    }

    public String cancelBooking(String room, String date, String start, String end) {
        String[] startParts = start.split(":");
        String[] endParts = end.split(":");
        if (startParts.length != 2 || endParts.length != 2) {
            return "ERROR: time must look like HH:MM";
        }
        long startMinutes;
        long endMinutes;
        try {
            startMinutes = Long.parseLong(startParts[0]) * 60 + Long.parseLong(startParts[1]);
            endMinutes = Long.parseLong(endParts[0]) * 60 + Long.parseLong(endParts[1]);
        } catch (NumberFormatException e) {
            return "ERROR: time must look like HH:MM";
        }

        boolean removed = store.removeSlot(room, date, startMinutes, endMinutes);
        if (!removed) {
            return "ERROR: no booking for " + room + " on " + date + " at " + start;
        }
        return "OK: cancelled " + room + " on " + date + " " + start + " to " + end;
    }

    public String rescheduleBooking(String room, String date, String oldStart, String oldEnd,
                                    String newStart, String newEnd) {
        String[] oldStartParts = oldStart.split(":");
        String[] oldEndParts = oldEnd.split(":");
        String[] newStartParts = newStart.split(":");
        String[] newEndParts = newEnd.split(":");
        if (oldStartParts.length != 2 || oldEndParts.length != 2
                || newStartParts.length != 2 || newEndParts.length != 2) {
            return "ERROR: time must look like HH:MM";
        }
        long oldStartMinutes;
        long oldEndMinutes;
        long newStartMinutes;
        long newEndMinutes;
        try {
            oldStartMinutes = Long.parseLong(oldStartParts[0]) * 60 + Long.parseLong(oldStartParts[1]);
            oldEndMinutes = Long.parseLong(oldEndParts[0]) * 60 + Long.parseLong(oldEndParts[1]);
            newStartMinutes = Long.parseLong(newStartParts[0]) * 60 + Long.parseLong(newStartParts[1]);
            newEndMinutes = Long.parseLong(newEndParts[0]) * 60 + Long.parseLong(newEndParts[1]);
        } catch (NumberFormatException e) {
            return "ERROR: time must look like HH:MM";
        }
        if (newEndMinutes <= newStartMinutes) {
            return "ERROR: end must be after start";
        }

        String user = store.bookerFor(room, date, oldStartMinutes, oldEndMinutes);
        if (user == null) {
            return "ERROR: no booking for " + room + " on " + date + " at " + oldStart;
        }

        store.removeSlot(room, date, oldStartMinutes, oldEndMinutes);
        store.addSlot(room, date, newStartMinutes, newEndMinutes, user);
        return "OK: moved " + room + " on " + date + " to " + newStart + " to " + newEnd;
    }

    public String listBookings(String room, String date) {
        List<long[]> slots = store.slotsFor(room, date);
        if (slots.isEmpty()) {
            return "no bookings for " + room + " on " + date;
        }
        StringBuilder out = new StringBuilder();
        out.append("bookings for ").append(room).append(" on ").append(date).append(":");
        for (long[] slot : slots) {
            String user = store.bookerFor(room, date, slot[0], slot[1]);
            out.append("\n  ")
               .append(minutesToText(slot[0]))
               .append(" to ")
               .append(minutesToText(slot[1]))
               .append(" (")
               .append(user)
               .append(")");
        }
        return out.toString();
    }

    private String minutesToText(long minutes) {
        long hours = minutes / 60;
        long rest = minutes % 60;
        return (hours < 10 ? "0" : "") + hours + ":" + (rest < 10 ? "0" : "") + rest;
    }
}
