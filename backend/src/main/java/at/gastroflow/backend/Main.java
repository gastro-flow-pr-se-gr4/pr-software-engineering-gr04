package at.gastroflow.backend;

import at.gastroflow.backend.dao.ProductDAO;
import at.gastroflow.backend.database.DatabaseConnection;
import at.gastroflow.backend.model.Product;

import java.math.BigDecimal;
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


    try{
        ProductDAO productDAO = new ProductDAO();

        //Neue Produkte Speichern
        Product product = new Product("Wiener Schnitzel", new BigDecimal("14.50"), "Mit Pommes auf Wunsch auch mit Reis oder Kartoffeln", true);
        productDAO.save(product);

        System.out.println("Produkt gespeichert!");

        //Alle anzeigen
        System.out.println("Gespeicherte Produkte: ");
        for (Product p : productDAO.findAll()) {
            System.out.println(p.getName() + " - " + p.getPrice() + " €");
        }

        //Mit ID suchen
        Product found = productDAO.findById(product.getId());
        if(found != null){
            System.out.println("Produkt gefunden: " + found.getName());
        }

        //Bearbeiten
        Product change = new Product(product.getId(),  "Wiener Schnitzel", new BigDecimal("15.90"), "Mit Pommes oder Reis", true);
        productDAO.update(change);
        System.out.println("Produkt bearbeitet!");

        productDAO.delete(product);
        System.out.println("Produkt löschen!");
    } catch(SQLException e) {
        System.out.println("Fehler beim Speichern des Produktes!");
        System.out.println(e.getMessage());
    }
    }
}

