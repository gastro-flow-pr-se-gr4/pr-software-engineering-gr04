package at.gastroflow.backend.service;

import at.gastroflow.backend.dao.ProductDAO;
import at.gastroflow.backend.model.Product;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class ProductService {
    private final ProductDAO productDAO =  new ProductDAO();

    public List<Product> getAllProducts() throws SQLException{
        return productDAO.findAll();
    }

    public Product getProductById(UUID id) throws SQLException{
        return productDAO.findById(id);
    }

    public void saveProduct(Product product) throws SQLException{
        if(product == null){
            throw new IllegalArgumentException("Produkt darf nicht null sein!");
        }

        if(product.getName() == null || product.getName().isEmpty()){
            throw new IllegalArgumentException("Produktname darf nicht leer sein!");
        }

        if(product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0 ){
            throw new IllegalArgumentException("Preis darf nicht kleiner 0 sein!");
        }

        productDAO.save(product);
    }

    public void updateProduct(Product product) throws SQLException{
        if(product == null){
            throw new IllegalArgumentException("Produkt darf nicht null sein!");
        }

        if(product.getName() == null || product.getName().isEmpty()){
            throw new IllegalArgumentException("Produktname darf nicht leer sein!");
        }

        if(product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0 ){
            throw new IllegalArgumentException("Preis darf nicht kleiner 0 sein!");
        }

        productDAO.update(product);
    }

    public void deleteProduct(UUID id) throws SQLException{
        if(id == null){
            throw new IllegalArgumentException("Produkt id darf nicht null sein!");
        }
        productDAO.delete(id);
    }


}
