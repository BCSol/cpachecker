typedef unsigned long size_t;
struct node {
   int h ;
   struct node *n ;
};
typedef struct node *List;
extern int __VERIFIER_nondet_int() ;
extern  __attribute__((__nothrow__)) void *( __attribute__((__leaf__)) malloc)(size_t __size )  __attribute__((__malloc__)) ;
int num  ;
int num2  ;
int flag1  ;
int sum  ;
float mean  ;
int flag  =    1;
int inter  ;
List successor  ;
List newHead  ;
int is_empty(List head );
List extract_even(List head );
float calc_mean(List head );
float variance(List head );
int main(void);
int __return_652;
int main()
{
List l ;
void *tmp ;
List temp ;
int size ;
int tmp___0 ;
int i ;
List next ;
void *tmp___1 ;
List le ;
List tmp___2 ;
float m ;
float tmp___3 ;
int tmp___4 ;
tmp = malloc(8);
l = (List )tmp;
temp = l;
if (((unsigned long)temp) == ((unsigned long)((void *)0)))
{
 __return_652 = -1;
return 1;
}
else 
{
tmp___0 = __VERIFIER_nondet_int();
size = tmp___0;
i = 0;
label_598:; 
if (i < size)
{
tmp___1 = malloc(8);
next = (List )tmp___1;
if (((unsigned long)next) != ((unsigned long)((void *)0)))
{
next->n = l;
l = next;
goto label_643;
}
else 
{
label_643:; 
i = i + 1;
goto label_598;
}
}
else 
{
{
List __tmp_1 = l;
List head = __tmp_1;
void *tmp ;
newHead = (List )((void *)0);
label_609:; 
if (((head->h) % 2) == 0)
{
successor = newHead;
tmp = malloc(8);
newHead = (List )tmp;
if (((unsigned long)newHead) == ((unsigned long)((void *)0)))
{
newHead = successor;
goto label_632;
}
else 
{
newHead->n = successor;
label_632:; 
goto label_620;
}
}
else 
{
label_620:; 
goto label_609;
}
}
}
}
}
