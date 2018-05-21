package ygodeckprices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParserFactory;

/**
 * Created 10/04/16.
 */
public class Deck {
    public enum Slot {
        MAIN, EXTRA, SIDE
    }
    
    static final String NL = System.lineSeparator();

    String creator;

    Map<String, Integer> main;
    Map<String, Integer> extra;
    Map<String, Integer> side;
    List<Map<String, Integer>> slots;

    public Deck() {
        creator = null;
        main    = new HashMap<>();
        extra   = new HashMap<>();
        side    = new HashMap<>();
        slots   = new ArrayList<>();

        slots.add(main);
        slots.add(extra);
        slots.add(side);
    }

    public Deck(File file) throws IOException {
        this();

        BufferedReader r = new BufferedReader(new FileReader(file));
        Map<String, Integer> current = main;
        String in, inLC;

        while((in = r.readLine()) != null) {
            if (in.startsWith("#") || in.startsWith("!")) {
                inLC = in.toLowerCase();
                if (inLC.contains("main")) {
                    current = main;
                } else if (inLC.contains("extra")) {
                    current = extra;
                } else if (inLC.contains("side")) {
                    current = side;
                } else if (inLC.contains("created by")) {
                    creator = in.substring(inLC.indexOf("created by") + "created by".length() + 1);
                }
            } else {
                if (current.containsKey(in)) {
                    current.put(in, current.get(in) + 1);
                } else {
                    current.put(in, 1);
                }
            }
        }
    }

    public void numbersToNames(Connection[] conns) throws SQLException {
        for (Map<String, Integer> slot : slots) {
            slot = getNames(slot, conns);
        }
    }
    
    public static Map<String, Integer> getNames(Map<String, Integer> cards, Connection[] conns) {
        Map<String, Integer> newCards = new HashMap<>();
        
        cards.entrySet().stream().forEach((card) -> {
            newCards.put(getName(card.getKey(), conns), card.getValue());
        });
        
        return newCards;
    }
    
    public static String getName(String id, Connection[] conns) {
        final String PREFIX = "SELECT name FROM texts WHERE id=";
        final String SUFFIX = ";";
        PreparedStatement stmt;
        ResultSet rs;
        String name;
        
        for (Connection c : conns) {
            try {
                stmt = c.prepareStatement(PREFIX + id + SUFFIX);
                rs = stmt.executeQuery();
                if (rs.next()) {
                    name = rs.getString(1);
                    System.out.println("Card ID " + id + ": " + name);
                    return name;
                }
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        
        System.err.println("Card ID " + id + ": missing from databases");
        return id;
   }

    public String price() throws IOException {
        final String PREFIX = "http://yugiohprices.com/api/get_card_prices/";
        final JsonParserFactory jpf = Json.createParserFactory(null);
        StringBuilder out = new StringBuilder();
        float t = 0f;
        float st = 0f;
        String name;
        int number;
        URL url;
        HttpURLConnection c;
        int rc;
        JsonParser jp;
        String s;
        JsonParser.Event e;
        float price;

        for (Map<String, Integer> slot : slots) {
            for (Map.Entry card : slot.entrySet()) {
                name = (String) card.getKey();
                number = (int) card.getValue();

                url = new URL(PREFIX + name);
                c = (HttpURLConnection) url.openConnection();

                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");

                rc = c.getResponseCode();
                if (rc != 200) {
                    throw new RuntimeException("HTTP error code " + rc);
                }

                jp = jpf.createParser(c.getInputStream());

                s = "";

                while (jp.hasNext()) {
                    e = jp.next();
                    if (e == JsonParser.Event.KEY_NAME) {
                        s = jp.getString();
                    } else if (e == JsonParser.Event.VALUE_NUMBER && s.equals("low")) {
                        price = jp.getBigDecimal().floatValue();

                        out.append(String.format("%-60s $%6.2f", name, price));
                        if (number != 1) {
                            out.append(" x");
                            out.append(number);
                        }
                        out.append(NL);

                        st += (price * number);

                        s = "";

                        break;
                    } else if (e == JsonParser.Event.VALUE_STRING && s.equals("status") && jp.getString().equals("fail")) {
                        System.err.println(name + " not found on yugiohprices.com");
                    }
                }
            }
            out.append(String.format("%-60s $%6.2f", " Total", st));
            out.append(NL).append(NL);
            t += st;
            st = 0f;
        }
        out.append(String.format("%-60s $%6.2f","  TOTAL", t));
        return out.toString();
    }

    public void add(String card, Slot slot) {
        switch (slot) {
            case MAIN:
                addMain(card);
                break;
            case EXTRA:
                addExtra(card);
                break;
            case SIDE:
                addSide(card);
                break;
        }
    }

    public void addMain(String card) {
        add(card, main);
    }

    public void addExtra(String card) {
        add(card, extra);
    }

    public void addSide(String card) {
        add(card, side);
    }

    private void add(String card, Map<String, Integer> slot) {
        Integer present = slot.get(card);
        if (present == null) {
            slot.put(card, 1);
        } else {
            slot.put(card, present + 1);
        }
    }

    public Map<String, Integer> get(Slot slot) {
        switch (slot) {
            case MAIN:
                return getMain();
            case EXTRA:
                return getExtra();
            case SIDE:
                return getSide();
            default:
                return null;
        }
    }

    public Map<String, Integer> getMain() {
        return main;
    }

    public Map<String, Integer> getExtra() {
        return extra;
    }

    public Map<String, Integer> getSide() {
        return side;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("MAIN:")
                .append(NL)
                .append(toString(main))
                .append(NL)
                .append("EXTRA:")
                .append(NL)
                .append(toString(extra))
                .append(NL)
                .append("SIDE:")
                .append(NL)
                .append(toString(side))
                .toString();
    }

    private String toString(Map<String, Integer> slot) {
        StringBuilder out = new StringBuilder();

        if (slot.size() > 0) {
            for (Map.Entry<String, Integer> card : slot.entrySet()) {
                out.append(card.getKey());
                out.append(": ");
                out.append(card.getValue());
                out.append(NL);
            }
        }

        return out.toString();
    }
}