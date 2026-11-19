package at.gastroflow.backend.model;

import java.util.UUID;

public class BaseEntity {
    private UUID id;

    public BaseEntity(){
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public BaseEntity(UUID id) {
        this.id = id;
    }
}
