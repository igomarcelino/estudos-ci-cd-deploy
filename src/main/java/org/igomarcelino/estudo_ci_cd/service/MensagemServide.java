package org.igomarcelino.estudo_ci_cd.service;

import org.igomarcelino.estudo_ci_cd.dto.MensagemRequestDTO;
import org.igomarcelino.estudo_ci_cd.dto.MensagemResponseDTO;
import org.igomarcelino.estudo_ci_cd.entity.Mensagem;
import org.igomarcelino.estudo_ci_cd.repository.MensagemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MensagemServide {

    private final MensagemRepository mensagemRepository;

    public MensagemServide(MensagemRepository mensagemRepository) {
        this.mensagemRepository = mensagemRepository;
    }

    public MensagemResponseDTO criarMensagem(MensagemRequestDTO dtoRequest){
        if (dtoRequest.conteudo().isEmpty() || dtoRequest.conteudo().isBlank()){
            throw new RuntimeException("Mensagem vazia, verifique");
        }

        Mensagem mensagem = new Mensagem();
        mensagem.setConteudo(dtoRequest.conteudo());
        var novaMensagem = mensagemRepository.save(mensagem);

        return new MensagemResponseDTO(novaMensagem);
    }

    public List<MensagemResponseDTO> mensagens(){
        return mensagemRepository.findAll()
                .stream().map(MensagemResponseDTO::new)
                .toList();
    }
}
