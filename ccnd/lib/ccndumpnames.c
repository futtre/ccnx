/*
 * Dumps names of everything quickly retrievable to stdout
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ccn/ccn.h>
#include <ccn/charbuf.h>
#include <ccn/uri.h>

/***********
<Interest>
  <Name/>
  <NameComponentCount>0</NameComponentCount>
  <OrderPreference>4</OrderPreference>
  <Scope>0</Scope>
</Interest>
**********/
static const unsigned char templ_ccnb[20] =
        "\001\322\362\000\002\212\216\060"
        "\000\002\362\216\064\000\002\322"
        "\216\060\000\000";
enum ccn_upcall_res
incoming_content(
    struct ccn_closure *selfp,
    enum ccn_upcall_kind kind,
    struct ccn_upcall_info *info)
{
    struct ccn_charbuf *c = NULL;
    struct ccn_charbuf *templ = NULL;
    const unsigned char *ccnb = NULL;
    size_t ccnb_size = 0;
    struct ccn_indexbuf *comps = NULL;
    int res;
    if (kind == CCN_UPCALL_FINAL)
        return(0);
    if (kind == CCN_UPCALL_INTEREST_TIMED_OUT)
        return(CCN_UPCALL_RESULT_REEXPRESS);
    if (kind != CCN_UPCALL_CONTENT)
        return(-1);
    ccnb = info->content_ccnb;
    ccnb_size = info->pco->offset[CCN_PCO_E];
    comps = info->content_comps;
    c = ccn_charbuf_create();
    res = ccn_uri_append(c, ccnb, ccnb_size, 1);
    if (res >= 0)
        printf("%s\n", ccn_charbuf_as_string(c));
    else
        fprintf(stderr, "*** Error: ccndumpnames line %d kind=%d res=%d\n",
            __LINE__, kind, res);
    /* Use the name of the content just received as the resumption point */
    ccn_name_init(c);
    ccn_name_append_components(c, ccnb, comps->buf[0], comps->buf[comps->n-1]);
    /* Use the full name, including digest, to ensure we move along */
    ccn_digest_ContentObject(ccnb, info->pco);
    ccn_name_append(c, info->pco->digest, info->pco->digest_bytes);
    templ = ccn_charbuf_create();
    ccn_charbuf_append(templ, templ_ccnb, 20);
    ccn_express_interest(info->h, c, 0, selfp, templ);
    ccn_charbuf_destroy(&templ);
    ccn_charbuf_destroy(&c);
    selfp->data = selfp; /* make not NULL to indicate we got something */
    return(0);
}

/* Use some static data for this simple program */
static struct ccn_closure incoming_content_action = {
    .p = &incoming_content
};

int
main(int argc, char **argv)
{
    struct ccn *ccn = NULL;
    struct ccn_charbuf *c = NULL;
    struct ccn_charbuf *templ = NULL;
    int i;
    ccn = ccn_create();
    if (ccn_connect(ccn, NULL) == -1) {
        perror("Could not connect to ccnd");
        exit(1);
    }
    c = ccn_charbuf_create();
    templ = ccn_charbuf_create();
    /* set scope to only address ccnd */
    ccn_charbuf_append(templ, templ_ccnb, 20);
    
    ccn_name_init(c);
    ccn_express_interest(ccn, c, 0, &incoming_content_action, templ);
    for (i = 0;; i++) {
        incoming_content_action.data = NULL;
        ccn_run(ccn, 100); /* stop if we run dry for 1/10 sec */
        fflush(stdout);
        if (incoming_content_action.data == NULL)
            break;
    }
    ccn_destroy(&ccn);
    ccn_charbuf_destroy(&c);
    ccn_charbuf_destroy(&templ);
    exit(0);
}
