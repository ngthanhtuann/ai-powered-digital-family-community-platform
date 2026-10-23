package com.familyconnect.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaAccessTest {

    private final MembershipQuery membership = mock(MembershipQuery.class);
    private final GenealogyQuery genealogy = mock(GenealogyQuery.class);
    private final MediaAccess access = new MediaAccess(membership, genealogy);
    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, List.of("MEMBER")), null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void heritageFileUsesOwnerIdAsFamily() {
        access.requireAccess("HERITAGE", familyId);
        verify(membership).requireFamilyAccess(userId, familyId);
    }

    @Test
    void personFileUsesFamilyOfThePerson() {
        UUID personId = UUID.randomUUID();
        when(genealogy.findPersonFamilyId(personId)).thenReturn(Optional.of(familyId));
        access.requireAccess("person", personId);
        verify(membership).requireFamilyAccess(userId, familyId);
    }

    @Test
    void otherFamilyIsRejected() {
        doThrow(new ApiException(ErrorCode.PERM_001)).when(membership).requireFamilyAccess(userId, familyId);
        assertCode(() -> access.requireAccess("HERITAGE", familyId), ErrorCode.PERM_001);
    }

    @Test
    void unknownPersonIsNotFoundAndUnknownTypeIsInvalid() {
        when(genealogy.findPersonFamilyId(any())).thenReturn(Optional.empty());
        assertCode(() -> access.requireAccess("PERSON", UUID.randomUUID()), ErrorCode.NOT_FOUND_001);
        assertCode(() -> access.requireAccess("POST", UUID.randomUUID()), ErrorCode.VALID_001);
        verify(membership, never()).requireFamilyAccess(any(), any());
    }

    @Test
    void anonymousCallerIsRejected() {
        SecurityContextHolder.clearContext();
        assertCode(() -> access.requireAccess("HERITAGE", familyId), ErrorCode.AUTH_002);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(code);
    }
}
