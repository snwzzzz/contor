package com.example.contor.service;

import com.example.contor.api.model.LabelDto;
import com.example.contor.api.model.NewLabelCommand;
import com.example.contor.api.model.UpdateLabelCommand;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.HexColor;
import com.example.contor.domain.Label;
import com.example.contor.domain.Slug;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.LabelNotFoundException;
import com.example.contor.mapper.LabelMapper;
import com.example.contor.persistence.LabelRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class LabelService {

    private final LabelRepository labelRepository;

    @Transactional
    public LabelDto createLabel(NewLabelCommand command) {
        Slug slug = new Slug(command.getSlug());
        HexColor color = new HexColor(command.getColor());
        if (labelRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("A label with slug '" + slug.value() + "' already exists");
        }

        Label label = Label.builder()
                .slug(slug)
                .name(command.getName())
                .color(color)
                .description(command.getDescription())
                .createdBy(CreatedBy.USER)
                .build();

        return LabelMapper.toDto(labelRepository.saveAndFlush(label));
    }

    @Transactional
    public List<LabelDto> findAll() {
        return labelRepository.findAllByOrderByNameAsc().stream()
                .map(LabelMapper::toDto)
                .toList();
    }

    @Transactional
    public LabelDto findById(Long id) {
        return labelRepository.findById(new Label.LabelId(id))
                .map(LabelMapper::toDto)
                .orElseThrow(() -> new LabelNotFoundException(id));
    }

    @Transactional
    public LabelDto updateLabel(Long id, UpdateLabelCommand command) {
        Label label = labelRepository.findById(new Label.LabelId(id))
                .orElseThrow(() -> new LabelNotFoundException(id));

        if (command.getName() != null) {
            label.setName(command.getName());
        }
        if (command.getColor() != null) {
            label.setColor(new HexColor(command.getColor()));
        }
        if (command.getDescription() != null) {
            label.setDescription(command.getDescription());
        }

        return LabelMapper.toDto(labelRepository.saveAndFlush(label));
    }

    @Transactional
    public void deleteLabel(Long id) {
        if (!labelRepository.existsById(new Label.LabelId(id))) {
            throw new LabelNotFoundException(id);
        }
        labelRepository.deleteById(new Label.LabelId(id));
    }
}
