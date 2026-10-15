package com.marketplace.account.internal.controller;

import com.marketplace.account.internal.service.TermsService;
import com.marketplace.openapi.api.PublicAccountApi;
import com.marketplace.openapi.model.TermsDocument;
import com.marketplace.openapi.model.TermsDocumentType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The latest terms, readable by anyone before registering. */
@RestController
public class PublicAccountController implements PublicAccountApi {

  private final TermsService terms;

  PublicAccountController(TermsService terms) {
    this.terms = terms;
  }

  @Override
  public ResponseEntity<TermsDocument> getLatestTerms(TermsDocumentType documentType) {
    return ResponseEntity.ok(TermsMapper.toApi(terms.latest(TermsMapper.fromApi(documentType))));
  }
}
