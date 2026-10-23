package ru.gbp.PolySignals.client.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.service.WhaleService;

import java.util.List;

@RestController
@RequestMapping("/api/whales")
@RequiredArgsConstructor
public class WhaleController {

    private final WhaleService whaleService;

    @PostMapping
    public ResponseEntity<Whale> addWhale(@Valid @RequestBody AddWhaleRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(whaleService.addWhale(request.address(), request.name()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{address}")
    public ResponseEntity<Void> removeWhale(@PathVariable String address) {
        return whaleService.removeWhale(address)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping
    public List<Whale> listWhales() {
        return whaleService.getAllWhales();
    }

    public record AddWhaleRequest(
            @NotBlank @Size(max = 128) String address,
            @Size(max = 255) String name
    ) {
    }
}
