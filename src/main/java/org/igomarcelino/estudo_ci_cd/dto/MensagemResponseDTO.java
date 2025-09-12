package org.igomarcelino.estudo_ci_cd.dto;

import org.igomarcelino.estudo_ci_cd.entity.Mensagem;

public record MensagemResponseDTO(
        long id,
        String conteudo
) {
    public MensagemResponseDTO(Mensagem mensagem){
        this(mensagem.getId_mensagem(), mensagem.getConteudo());
    }
}
