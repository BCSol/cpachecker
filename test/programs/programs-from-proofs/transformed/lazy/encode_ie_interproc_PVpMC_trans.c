void *__builtin_alloca(unsigned long  ) ;
int flag  =    0;
void main(void);
void main()
{
int i ;
int ielen ;
int leader_len ;
int bufsize ;
char *buf ;
unsigned long __lengthofbuf ;
void *tmp ;
int index ;
unsigned long __cil_tmp9 ;
char *__cil_tmp10 ;
int __cil_tmp11 ;
char *__cil_tmp12 ;
i = 0;
ielen = 5;
leader_len = 1;
bufsize = 6;
__lengthofbuf = (unsigned long)bufsize;
__cil_tmp9 = 1UL * __lengthofbuf;
tmp = __builtin_alloca(__cil_tmp9);
buf = (char *)tmp;
if (bufsize < leader_len)
{
goto label_159;
}
else 
{
bufsize = bufsize - leader_len;
index = leader_len;
if (i < ielen)
{
if (bufsize > 2)
{
flag = index;
__cil_tmp10 = buf + index;
*__cil_tmp10 = 120;
flag = index + 1;
__cil_tmp11 = index + 1;
__cil_tmp12 = buf + __cil_tmp11;
*__cil_tmp12 = 120;
index = index + 2;
bufsize = bufsize - 2;
i = i + 1;
if (i < ielen)
{
if (bufsize > 2)
{
flag = index;
__cil_tmp10 = buf + index;
*__cil_tmp10 = 120;
flag = index + 1;
__cil_tmp11 = index + 1;
__cil_tmp12 = buf + __cil_tmp11;
*__cil_tmp12 = 120;
index = index + 2;
bufsize = bufsize - 2;
i = i + 1;
if (i < ielen)
{
goto label_210;
}
else 
{
label_210:; 
return 1;
}
}
else 
{
goto label_183;
}
}
else 
{
label_183:; 
return 1;
}
}
else 
{
goto label_156;
}
}
else 
{
label_156:; 
label_159:; 
return 1;
}
}
}
