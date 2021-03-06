package com.markupartist.sthlmtraveling.provider.deviation;

import android.content.Context;
import android.text.format.Time;
import android.util.Log;
import android.util.TimeFormatException;

import com.markupartist.sthlmtraveling.data.misc.HttpHelper;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.markupartist.sthlmtraveling.provider.ApiConf.apiEndpoint2;

public class DeviationStore {
    static String TAG = "DeviationStore";
    private static String LINE_PATTERN = "[A-Za-zåäöÅÄÖ ]?([\\d]+)[ A-Z]?";
    private static Pattern sLinePattern = Pattern.compile(LINE_PATTERN);

    public ArrayList<Deviation> getDeviations(final Context context)
            throws IOException {
        ArrayList<Deviation> deviations = new ArrayList<Deviation>();

        try {
            String deviationsRawJson = retrieveDeviations(context);

            JSONObject jsonDeviations = new JSONObject(deviationsRawJson); 

            JSONArray jsonArray = jsonDeviations.getJSONArray("deviations");
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject jsonDeviation = jsonArray.getJSONObject(i);

                    Time created = new Time();
                    created.parse(jsonDeviation.getString("created"));

                    Deviation deviation = new Deviation();
                    deviation.setCreated(created);
                    deviation.setDetails(stripNewLinesAtTheEnd(jsonDeviation.getString("description")));
                    deviation.setHeader(jsonDeviation.getString("header"));
                    //deviation.setLink(jsonDeviation.getString("link"));
                    //deviation.setMessageVersion(jsonDeviation.getInt("messageVersion"));
                    deviation.setReference(jsonDeviation.getLong("reference"));
                    deviation.setScope(jsonDeviation.getString("scope"));
                    deviation.setScopeElements(jsonDeviation.getString("scope_elements"));
                    //deviation.setSortOrder(jsonDeviation.getInt("sortOrder"));

                    deviations.add(deviation);
                } catch (TimeFormatException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return deviations;
    }

    private String retrieveDeviations(final Context context) throws IOException {
        final String endpoint = apiEndpoint2() + "v1/deviation/";

        HttpHelper httpHelper = HttpHelper.getInstance(context);
        Response response = httpHelper.getClient().newCall(
                httpHelper.createRequest(endpoint)
        ).execute();

        if (!response.isSuccessful()) {
            throw new IOException("A remote server error occurred when getting deviations.");
        }

        return response.body().string();
    }

    public static ArrayList<Deviation> filterByLineNumbers(
            ArrayList<Deviation> deviations, ArrayList<Integer> lineNumbers) {
        if (lineNumbers.isEmpty()) {
            return deviations;
        }

        ArrayList<Deviation> filteredList = new ArrayList<Deviation>();
        for (Deviation deviation : deviations) {
            ArrayList<Integer> lines = extractLineNumbers(
                    deviation.getScopeElements(), null);
            //Log.d(TAG, "Matching " + lineNumbers.toString() + " against " + lines);
            for (int line : lineNumbers) {
                if (lines.contains(line)) {
                    filteredList.add(deviation);
                    // A deviation can span over several line numbers.
                    // Break on the first criteria match to not add the same
                    // deviation more than once.
                    break;
                }
            }
        }

        return filteredList;
    }

    /**
     * Extract integer from the passed string recursively.
     * @param scope the string
     * @param foundIntegers previous found integer, pass null if you want to 
     * start from scratch
     * @return the found integers or a empty ArrayList if none found
     */
    public static ArrayList<Integer> extractLineNumbers(String scope,
            ArrayList<Integer> foundIntegers) {
        if (foundIntegers == null)
            foundIntegers = new ArrayList<Integer>();

        Matcher matcher = sLinePattern.matcher(scope);
        boolean matchFound = matcher.find(); 

        if (matchFound) {
            foundIntegers.add(Integer.parseInt(matcher.group(1)));
            scope = scope.replaceFirst(matcher.group(1), ""); // remove what we found.
        } else {
            return foundIntegers;
        }

        return extractLineNumbers(scope, foundIntegers);
    }

    private String stripNewLinesAtTheEnd(String value) {
        if (value.endsWith("\n")) {
            value = value.substring(0, value.length() - 2);
            stripNewLinesAtTheEnd(value);
        }
        return value;
    }

    public TrafficStatus getTrafficStatus(final Context context) throws IOException {
        final String endpoint = apiEndpoint2() + "v1/trafficstatus/";

        HttpHelper httpHelper = HttpHelper.getInstance(context);
        Response response = httpHelper.getClient().newCall(
                httpHelper.createRequest(endpoint)).execute();

        if (!response.isSuccessful()) {
            throw new IOException("A remote server error occurred when getting traffic status.");
        }

        String rawContent = response.body().string();

        TrafficStatus ts = null;
        try {
            ts = TrafficStatus.fromJson(new JSONObject(rawContent));            
        } catch (JSONException e) {
            Log.d(TAG, "Could not parse the reponse...");
            throw new IOException("Could not parse the response.");
        }

        return ts;
    }

    /**
     *
     */
    public static class TrafficStatus {
        public static final int GOOD = 1;
        public static final int MINOR = 2;
        public static final int MAJOR = 3;

        public ArrayList<TrafficType> trafficTypes = new ArrayList<TrafficType>();

        @Override
        public String toString() {
            return "TrafficStatus{" +
                    "trafficTypes=" + trafficTypes +
                    '}';
        }

        public static TrafficStatus fromJson(JSONObject jsonObject)
                throws JSONException {
            TrafficStatus ts = new TrafficStatus();            
            JSONArray jsonArray = jsonObject.getJSONArray("traffic_status");
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    ts.trafficTypes.add(TrafficType.fromJson(jsonArray.getJSONObject(i)));
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Failed to parse traffic type: " + e.getMessage());
                }
            }
            return ts;
        }
    }

    /**
     * Represents a traffic type.
     */
    public static class TrafficType {
        public String type;
        public boolean expanded;
        public boolean hasPlannedEvent;
        public int status;
        public ArrayList<TrafficEvent> events = new ArrayList<TrafficEvent>();

        @Override
        public String toString() {
            return "TrafficType{" +
                    "type='" + type + '\'' +
                    ", expanded=" + expanded +
                    ", hasPlannedEvent=" + hasPlannedEvent +
                    ", status=" + status +
                    ", events=" + events +
                    '}';
        }

        public static TrafficType fromJson(JSONObject jsonObject)
                throws JSONException {
            TrafficType tt = new TrafficType();
            
            tt.type = jsonObject.getString("type");
            tt.expanded = jsonObject.getBoolean("expanded");
            tt.hasPlannedEvent = jsonObject.getBoolean("has_planned_event");
            tt.status = jsonObject.getInt("status");

            JSONArray jsonArray = jsonObject.getJSONArray("events");
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    tt.events.add(TrafficEvent.fromJson(jsonArray.getJSONObject(i)));
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Failed to parse event: " + e.getMessage());
                }
            }

            return tt;
        }
    }

    /**
     * Represents a traffic event.
     */
    public static class TrafficEvent {
        public String message;
        public boolean expanded;
        public boolean planned;
        public int sortIndex;
        public String infoUrl;
        public int status;

        @Override
        public String toString() {
            return "TrafficEvent{" +
                    "message='" + message + '\'' +
                    ", expanded=" + expanded +
                    ", planned=" + planned +
                    ", sortIndex=" + sortIndex +
                    ", infoUrl='" + infoUrl + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }

        public static TrafficEvent fromJson(JSONObject jsonObject)
                throws JSONException {
            TrafficEvent te = new TrafficEvent();
            te.message = jsonObject.getString("message");
            te.expanded = jsonObject.getBoolean("expanded");
            te.planned = jsonObject.getBoolean("planned");
            te.sortIndex = jsonObject.getInt("sort_index");
            te.infoUrl = jsonObject.getString("info_url");
            te.status = jsonObject.getInt("status");
            return te;
        }
    }
}
