import gql from 'graphql-tag';

const sharedTypes = gql`
    type ContentPage {
        id: ID!
        createdAt: DateTime!
        updatedAt: DateTime!
        slug: String!
        titel: String!
        inhaltHtml: String
        metaTitle: String
        metaDescription: String
        published: Boolean!
        imMenu: Boolean!
        menuTitel: String
        menuSortierung: Int!
    }
`;

/** Admin-API: vollständige CMS-Pflege (idempotenter Upsert über slug + Löschen). */
export const adminApiExtensions = gql`
    ${sharedTypes}

    input UpsertContentPageInput {
        slug: String!
        titel: String!
        inhaltHtml: String
        metaTitle: String
        metaDescription: String
        published: Boolean
        imMenu: Boolean
        menuTitel: String
        menuSortierung: Int
    }

    extend type Query {
        contentPages: [ContentPage!]!
    }

    extend type Mutation {
        upsertContentPage(input: UpsertContentPageInput!): ContentPage!
        deleteContentPage(slug: String!): Boolean!
    }
`;

/** Shop-API: nur veröffentlichte Seite (nach slug) + Menü-Liste. */
export const shopApiExtensions = gql`
    ${sharedTypes}

    extend type Query {
        contentPage(slug: String!): ContentPage
        menuPages: [ContentPage!]!
    }
`;
