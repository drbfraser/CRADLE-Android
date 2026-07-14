# Forms V2 API — testing

This document covers the manual API testing done for Phase 1 of the Forms V1 → V2 migration

---

## 1. Create a form template

**Request**
```
POST /forms/v2/templates/body
Content-Type: application/json

{
  "version": 1,
  "classification": {
    "name": { "english": "test" }
  },
  "questions": [
    {
      "order": 0,
      "questionType": "MULTIPLE_CHOICE",
      "questionText": { "english": "Select symptoms" },
      "required": true,
      "mcOptions": [
        { "stringId": "opt-headache", "translations": { "english": "Headache" } },
        { "stringId": "opt-fever", "translations": { "english": "Fever" } },
        { "stringId": "opt-nausea", "translations": { "english": "Nausea" } }
      ]
    },
    {
      "order": 1,
      "questionType": "MULTIPLE_SELECT",
      "questionText": { "english": "Select all that apply" },
      "required": false,
      "mcOptions": [
        { "stringId": "opt-yes", "translations": { "english": "Yes" } },
        { "stringId": "opt-no", "translations": { "english": "No" } }
      ]
    }
  ]
}
```

**Response** — `201 Created`
```json
{
  "id": "9814c567-21ab-453d-8699-b108658b1853",
  "formClassificationId": "9bb5b565-5759-48e3-8f7c-5f0d33873f50",
  "version": 1,
  "archived": false,
  "name": "test",
  "dateCreated": 1784048596
}
```

 `MULTIPLE_CHOICE`/`MULTIPLE_SELECT` was used to avoid a bug that was encountered