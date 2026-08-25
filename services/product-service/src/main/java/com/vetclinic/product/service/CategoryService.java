package com.vetclinic.product.service;

import com.vetclinic.product.domain.Category;
import com.vetclinic.product.dto.CategoryRequest;
import com.vetclinic.product.dto.CategoryResponse;
import com.vetclinic.product.exception.DuplicateResourceException;
import com.vetclinic.product.exception.ResourceNotFoundException;
import com.vetclinic.product.repository.CategoryRepository;
import com.vetclinic.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** CN-27: quản lý danh mục sản phẩm. */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Category slug already exists: " + request.slug());
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .build();

        // saveAndFlush chứ không phải save: @CreationTimestamp chỉ được điền lúc flush,
        // nếu không response của POST sẽ trả createdAt/updatedAt là null.
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = findOrThrow(id);

        // Chỉ báo trùng khi slug thực sự đổi sang slug của danh mục khác.
        if (!category.getSlug().equals(request.slug()) && categoryRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Category slug already exists: " + request.slug());
        }

        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());

        return toResponse(category);
    }

    @Transactional
    public void delete(UUID id) {
        Category category = findOrThrow(id);

        // Xoá danh mục còn sản phẩm sẽ làm sản phẩm mồ côi — chặn ngay ở tầng nghiệp vụ
        // để trả lỗi 409 rõ ràng thay vì để FK ném ra lỗi 500 khó hiểu.
        if (productRepository.existsByCategoryId(id)) {
            throw new DuplicateResourceException("Category still has products: " + id);
        }

        categoryRepository.delete(category);
    }

    private Category findOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
