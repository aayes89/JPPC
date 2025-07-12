ENTRY(_start)

MEMORY
{
  RAM  (rxw) : ORIGIN = 0x80000000, LENGTH = 64M
  VRAM (rw)  : ORIGIN = 0x10000000, LENGTH = 32M
}

SECTIONS
{
  . = 0x80010000;               /* Stack base y entrada */
  
  .text : ALIGN(4)
  {
    _text_start = .;
    *(.text)
    *(.text.*)
    _text_end = .;
  } > RAM

  .data : ALIGN(4)
  {
    _data_start = .;
    *(.data)
    *(.data.*)
    _data_end = .;
  } > RAM

  .bss : ALIGN(4)
  {
    _bss_start = .;
    *(.bss)
    *(COMMON)
    _bss_end = .;
  } > RAM

  . = ALIGN(16);
  _end = .;
}
