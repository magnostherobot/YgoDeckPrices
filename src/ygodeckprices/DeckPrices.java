package ygodeckprices;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created 08/04/16.
 */
public class DeckPrices {
    static final String NL = System.lineSeparator();
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java DeckPrices <files/folders>");
            System.exit(0);
        }
        List<File> deckFiles = getDeckFiles(args);
        printPrices(deckFiles);
    }

    private static List<File> getDeckFiles(String[] args) {
        List<File> files = new ArrayList<>();
        File file;
        
        for (String arg : args) {
            file = new File(arg);
            if (file.isFile() && arg.endsWith(".ydk")) {
                files.add(file);
            } else if (file.isDirectory()) {
                files.addAll(Arrays.asList(file.listFiles((File f, String name) -> name.endsWith(".ydk"))));
            }
        }
        
        return files;
    }
        
    private static void printPrices(List<File> files) throws Exception {
        PrintWriter w;
        String p;
        Connection[] connections = new Connection[2];

        Class.forName("org.sqlite.JDBC");
        connections[0] = connect("cards.cdb");
        connections[1] = connect("official.cdb");

        for (File file : files) {
            p = file.getName();
            w = new PrintWriter(p.substring(0, p.lastIndexOf(".") + 1) + "txt");
            Deck deck = new Deck(file);
            deck.numbersToNames(connections);
            w.print(deck.price());
            w.close();
        }
    }
    
    private static Connection connect(String name) throws SQLException {
        final String PREFIX = "jdbc:sqlite:";
        return DriverManager.getConnection(PREFIX + name);
    }
}