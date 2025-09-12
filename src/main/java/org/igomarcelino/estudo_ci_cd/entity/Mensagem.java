package org.igomarcelino.estudo_ci_cd.entity;

import jakarta.persistence.*;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ConditionalOnIssuerLocationJwtDecoder;

import java.util.Objects;

@Entity
@Table(name = "tbl_mensagem")
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_mensagem;

    private String conteudo;

    public Long getId_mensagem() {
        return id_mensagem;
    }


    public String getConteudo() {
        return conteudo;
    }

    public void setConteudo(String conteudo) {
        this.conteudo = conteudo;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Mensagem mensagem = (Mensagem) o;
        return Objects.equals(id_mensagem, mensagem.id_mensagem) && Objects.equals(conteudo, mensagem.conteudo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id_mensagem, conteudo);
    }
}
