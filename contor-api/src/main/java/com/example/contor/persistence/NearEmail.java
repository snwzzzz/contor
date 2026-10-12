package com.example.contor.persistence;

/**
 * An email close to another one in meaning (see {@link EmailEmbeddingRepository#findNearest}).
 *
 * @param distance cosine distance of the two vectors: 0 = same direction, 1 = unrelated
 */
public record NearEmail(Long emailId, double distance) {
}
