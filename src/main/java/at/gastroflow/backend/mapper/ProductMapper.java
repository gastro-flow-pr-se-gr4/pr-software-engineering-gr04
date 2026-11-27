package at.gastroflow.backend.mapper;

import at.gastroflow.backend.dto.ProductDTO;
import at.gastroflow.backend.model.Product;

public class ProductMapper {
    public ProductDTO toDTO(Product product) {
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                product.isAvailable()
        );
    }

    public Product toEntity(ProductDTO dto) {
        if (dto.getId() == null) {
            return new Product(
                dto.getName(),
                dto.getPrice(),
                dto.getDescription(),
                dto.isAvailable()
        );
        }
        return new Product(
                dto.getId(),
                dto.getName(),
                dto.getPrice(),
                dto.getDescription(),
                dto.isAvailable()
        );
    }
}
