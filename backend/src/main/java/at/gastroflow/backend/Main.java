package at.gastroflow.backend;

import at.gastroflow.backend.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) {

        try {
            Connection connection = DatabaseConnection.getConnection();

            System.out.println("Datenbankverbindung erfolgreich!");

            connection.close();

        } catch (SQLException e) {
            System.out.println("Fehler bei der Datenbankverbindung!");
            System.out.println(e.getMessage());
        }
    }
}

